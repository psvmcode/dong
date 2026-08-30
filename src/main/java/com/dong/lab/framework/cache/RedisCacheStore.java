package com.dong.lab.framework.cache;

import com.dong.lab.common.util.JsonUtils;
import com.dong.lab.framework.redis.RedisService;
import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * L2 分布式缓存，所有节点共享同一份数据。
 *
 * <p>与 L1 的关键差异：这里的 ttl 也要叠加抖动，
 * 否则一批 key 同时写入就会同时失效，雪崩会穿透到数据库。
 */
public class RedisCacheStore implements CacheStore {

    private static final String KEY_PREFIX = "lab:cache:";

    // 空值标记用字符串，不能用真正的 null，Redis 里 null 和不存在的 key 无法区分
    private static final String EMPTY_MARKER = "null";

    private final RedisService redisService;

    private final double jitterRatio;

    public RedisCacheStore(RedisService redisService, double jitterRatio) {
        this.redisService = redisService;
        this.jitterRatio = jitterRatio;
    }

    @Override
    public String name() {
        return "redis";
    }

    @Override
    public <T> CacheLookup<T> lookup(String key, Class<T> type) {
        String raw = redisService.get(prefixed(key)).orElse(null);
        return decode(raw, json -> JsonUtils.fromJson(json, type));
    }

    @Override
    public <T> CacheLookup<T> lookup(String key, TypeReference<T> type) {
        String raw = redisService.get(prefixed(key)).orElse(null);
        return decode(raw, json -> JsonUtils.fromJson(json, type));
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        redisService.set(prefixed(key), JsonUtils.toJson(value), jitter(ttl));
    }

    @Override
    public void putEmpty(String key, Duration ttl) {
        redisService.set(prefixed(key), EMPTY_MARKER, ttl);
    }

    @Override
    public void evict(String key) {
        redisService.delete(prefixed(key));
    }

    @Override
    public long estimatedSize() {
        Long size = redisService.template().execute(
                (org.springframework.data.redis.core.RedisCallback<Long>)
                        connection -> connection.serverCommands().dbSize());
        return size == null ? 0L : size;
    }

    private <T> CacheLookup<T> decode(String raw, Function<String, T> decoder) {
        if (raw == null) {
            return new CacheLookup.Miss<>();
        }
        if (EMPTY_MARKER.equals(raw)) {
            return new CacheLookup.Empty<>();
        }
        return new CacheLookup.Hit<>(decoder.apply(raw));
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

    private String prefixed(String key) {
        return KEY_PREFIX + key;
    }

}
