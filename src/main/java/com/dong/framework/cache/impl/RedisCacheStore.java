package com.dong.framework.cache.impl;

import com.dong.common.util.JsonUtils;
import com.dong.framework.cache.CacheCircuitBreaker;
import com.dong.framework.cache.CacheEntry;
import com.dong.framework.cache.CacheLookup;
import com.dong.framework.cache.CacheStore;
import com.dong.framework.redis.RedisService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * L2 分布式缓存，所有节点共享同一份数据。
 *
 * <p>与 L1 的关键差异：这里的 ttl 也要叠加抖动，
 * 否则一批 key 同时写入就会同时失效，雪崩会穿透到数据库。
 *
 * <p>两个必须做对的地方：
 * <ul>
 *     <li>Redis 故障必须就地降级为 Miss，绝不能把异常抛给读链路。
 *        缓存是加速件，它挂掉的后果应该是「慢」，而不是「整个接口 500」</li>
 *     <li>连续失败要触发熔断，跳过这一层直接回源。
 *        否则每个请求都要陪 Redis 等满超时，故障就从慢放大成不可用</li>
 *     <li>逻辑过期后旧值还要留一段宽容期，这段时间它不算命中，
 *        但回源失败时可以拿出来兜底</li>
 * </ul>
 */
@Slf4j
public class RedisCacheStore implements CacheStore {

    private static final String KEY_PREFIX = "lab:cache:";

    // 空值标记用字符串，不能用真正的 null，Redis 里 null 和不存在的 key 无法区分
    private static final String EMPTY_MARKER = "null";

    /**
     * Redis 服务。
     */
    private final RedisService redisService;

    /**
     * TTL 抖动比例。
     */
    private final double jitterRatio;

    /**
     * 逻辑过期后仍保留旧值的宽容期。
     */
    private final Duration staleGrace;

    /**
     * L2 熔断器。
     */
    private final CacheCircuitBreaker breaker;

    /**
     * 构造 Redis 缓存存储。
     *
     * @param redisService Redis 服务
     * @param jitterRatio  TTL 抖动比例
     * @param staleGrace   旧值宽容期
     * @param breaker      L2 熔断器
     */
    public RedisCacheStore(RedisService redisService, double jitterRatio, Duration staleGrace, CacheCircuitBreaker breaker) {
        this.redisService = redisService;
        this.jitterRatio = jitterRatio;
        this.staleGrace = staleGrace;
        this.breaker = breaker;
    }

    /**
     * 返回缓存层名称。
     *
     * @return 名称
     */
    @Override
    public String name() {
        return "redis";
    }

    /**
     * 查询缓存值。
     *
     * @param key  缓存键
     * @param type 值类型
     * @param <T>  值类型
     * @return 缓存查找结果
     */
    @Override
    public <T> CacheLookup<T> lookup(String key, Class<T> type) {
        return read(prefixed(key), value -> JsonUtils.fromJson(JsonUtils.toJson(value), type));
    }

    /**
     * 查询缓存值。
     *
     * @param key  缓存键
     * @param type 泛型类型引用
     * @param <T>  值类型
     * @return 缓存查找结果
     */
    @Override
    public <T> CacheLookup<T> lookup(String key, TypeReference<T> type) {
        return read(prefixed(key), value -> JsonUtils.fromJson(JsonUtils.toJson(value), type));
    }

    /**
     * 放入缓存。
     *
     * @param key   缓存键
     * @param value 缓存值
     * @param ttl   过期时间
     */
    @Override
    public void put(String key, Object value, Duration ttl) {
        if (!breaker.allowRequest()) {
            return;
        }
        try {
            Duration logicalTtl = jitter(ttl);
            redisService.set(prefixed(key), JsonUtils.toJson(CacheEntry.of(value, logicalTtl, staleGrace)), logicalTtl.plus(staleGrace));
            breaker.recordSuccess();
        } catch (Exception ex) {
            breaker.recordFailure();
            log.warn("l2 cache write failed, skipped key={}: {}", key, ex.getMessage());
        }
    }

    /**
     * 放入空值占位，用于防止缓存穿透。
     * 空值不需要宽容期：它本来就是「确认没有」的标记，留旧值没有意义。
     *
     * @param key 缓存键
     * @param ttl 过期时间
     */
    @Override
    public void putEmpty(String key, Duration ttl) {
        if (!breaker.allowRequest()) {
            return;
        }
        try {
            redisService.set(prefixed(key), EMPTY_MARKER, ttl);
            breaker.recordSuccess();
        } catch (Exception ex) {
            breaker.recordFailure();
            log.warn("l2 cache empty marker write failed, skipped key={}: {}", key, ex.getMessage());
        }
    }

    /**
     * 清除缓存。删除失败只记日志：
     * 缓存没删掉最坏是短暂不一致，而抛异常会让写操作整个失败。
     *
     * @param key 缓存键
     */
    @Override
    public void evict(String key) {
        try {
            redisService.delete(prefixed(key));
        } catch (Exception ex) {
            log.warn("l2 cache evict failed key={}: {}", key, ex.getMessage());
        }
    }

    /**
     * 估算缓存大小。
     *
     * @return 估算大小
     */
    @Override
    public long estimatedSize() {
        try {
            Long size = redisService.template().execute((org.springframework.data.redis.core.RedisCallback<Long>) connection -> connection.serverCommands().dbSize());
            return size == null ? 0L : size;
        } catch (Exception ex) {
            return 0L;
        }
    }

    /**
     * 读取并解码。熔断打开时直接返回 Miss，让上层走回源，
     * 这比陪着一个已经故障的依赖等超时要好得多。
     */
    private <T> CacheLookup<T> read(String redisKey, Function<Object, T> decoder) {
        if (!breaker.allowRequest()) {
            return new CacheLookup.Miss<>();
        }
        String raw;
        try {
            raw = redisService.get(redisKey).orElse(null);
        } catch (Exception ex) {
            breaker.recordFailure();
            log.warn("l2 cache read failed, degraded to miss: {}", ex.getMessage());
            return new CacheLookup.Miss<>();
        }
        breaker.recordSuccess();
        return decode(raw, decoder);
    }

    /**
     * 解码 Redis 返回值，区分命中、旧值、空值与未命中。
     *
     * @param raw     原始字符串
     * @param decoder 解码函数
     * @param <T>     值类型
     * @return 缓存查找结果
     */
    private <T> CacheLookup<T> decode(String raw, Function<Object, T> decoder) {
        if (raw == null) {
            return new CacheLookup.Miss<>();
        }
        if (EMPTY_MARKER.equals(raw)) {
            return new CacheLookup.Empty<>();
        }
        CacheEntry entry = JsonUtils.fromJson(raw, CacheEntry.class);
        if (entry == null || entry.value() == null || entry.discarded()) {
            return new CacheLookup.Miss<>();
        }
        T value = decoder.apply(entry.value());
        if (value == null) {
            return new CacheLookup.Miss<>();
        }
        return entry.expired() ? new CacheLookup.Stale<>(value) : new CacheLookup.Hit<>(value);
    }

    /**
     * 给 ttl 叠加随机增量，把集中过期打散。这是防雪崩的关键一步。
     */
    private Duration jitter(Duration ttl) {
        if (jitterRatio <= 0) {
            return ttl;
        }
        long extra = (long) (ttl.toMillis() * jitterRatio * ThreadLocalRandom.current().nextDouble());
        return ttl.plusMillis(extra);
    }

    /**
     * 为缓存键添加统一前缀。
     *
     * @param key 原始键
     * @return 带前缀的键
     */
    private String prefixed(String key) {
        return KEY_PREFIX + key;
    }

}
