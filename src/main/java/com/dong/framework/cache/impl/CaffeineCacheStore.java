package com.dong.framework.cache.impl;

import com.dong.common.util.JsonUtils;
import com.dong.framework.cache.CacheEntry;
import com.dong.framework.cache.CacheLookup;
import com.dong.framework.cache.CacheStore;
import com.dong.framework.cache.CacheEmpty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * L1 本地缓存，进程内持有，不跨节点共享。
 *
 * <p>踩坑提醒：JDK 21 之后 Caffeine 反射访问内部字段会触发模块权限告警，
 * 因此这里只存 CacheEntry 包装对象，避免框架去猜测泛型类型。
 *
 * <p>本地缓存也保留旧值宽容期：Redis 和数据库同时不可用时，
 * 进程内这份旧值是最后一道兜底，能挡住一部分请求不打向已经出问题的下游。
 */
public class CaffeineCacheStore implements CacheStore {

    /**
     * Caffeine 缓存实例。
     */
    private final Cache<String, CacheEntry> cache;

    /**
     * 逻辑过期后仍保留旧值的宽容期。
     */
    private final Duration staleGrace;

    /**
     * 构造本地缓存存储。
     *
     * @param maxSize    最大条目数
     * @param staleGrace 旧值宽容期
     */
    public CaffeineCacheStore(long maxSize, Duration staleGrace) {
        this.cache = Caffeine.newBuilder().maximumSize(maxSize).expireAfter(new EntryExpiry()).recordStats().build();
        this.staleGrace = staleGrace;
    }

    /**
     * 返回缓存层名称。
     *
     * @return 名称
     */
    @Override
    public String name() {
        return "caffeine";
    }

    /**
     * 四种结果必须区分清楚，不能只用 null 表示异常：
     * Miss 是完全没查到、Empty 是查到空值标记、Hit 是真正命中、
     * Stale 是逻辑已过期但还在，只能用于回源失败时兜底。
     *
     * @param key  缓存键
     * @param type 值类型
     * @param <T>  值类型
     * @return 缓存查找结果
     */
    @Override
    public <T> CacheLookup<T> lookup(String key, Class<T> type) {
        CacheEntry entry = cache.getIfPresent(key);
        if (entry == null) {
            return new CacheLookup.Miss<>();
        }
        if (entry.discarded()) {
            cache.invalidate(key);
            return new CacheLookup.Miss<>();
        }
        if (entry.value() == CacheEmpty.INSTANCE) {
            // 空值标记过期后必须当成 Miss：标记只说明「当时没有」，
            // 数据可能已经被创建出来了，继续拦下去就永远查不到新数据
            return entry.expired() ? new CacheLookup.Miss<>() : new CacheLookup.Empty<>();
        }
        T value = convert(entry.value(), type);
        return entry.expired() ? new CacheLookup.Stale<>(value) : new CacheLookup.Hit<>(value);
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
        CacheEntry entry = cache.getIfPresent(key);
        if (entry == null || entry.discarded()) {
            return new CacheLookup.Miss<>();
        }
        if (entry.value() == CacheEmpty.INSTANCE) {
            return entry.expired() ? new CacheLookup.Miss<>() : new CacheLookup.Empty<>();
        }
        T value = JsonUtils.fromJson(JsonUtils.toJson(entry.value()), type);
        return entry.expired() ? new CacheLookup.Stale<>(value) : new CacheLookup.Hit<>(value);
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
        cache.put(key, CacheEntry.of(value, ttl, staleGrace));
    }

    /**
     * 放入空值占位，用于防止缓存穿透。
     *
     * @param key 缓存键
     * @param ttl 过期时间
     */
    @Override
    public void putEmpty(String key, Duration ttl) {
        cache.put(key, CacheEntry.of(CacheEmpty.INSTANCE, ttl));
    }

    /**
     * 清除缓存。
     *
     * @param key 缓存键
     */
    @Override
    public void evict(String key) {
        cache.invalidate(key);
    }

    /**
     * 估算缓存大小。
     *
     * @return 估算大小
     */
    @Override
    public long estimatedSize() {
        return cache.estimatedSize();
    }

    /**
     * 清空本地缓存。
     */
    public void clear() {
        cache.invalidateAll();
    }

    /**
     * 获取 Caffeine 统计快照。
     *
     * @return 统计快照
     */
    public CacheStatsSnapshot snapshot() {
        var stats = cache.stats();
        return new CacheStatsSnapshot(stats.hitCount(), stats.missCount(), stats.evictionCount(), cache.estimatedSize());
    }

    /**
     * 本地缓存统计快照记录。
     *
     * @param hitCount      命中数
     * @param missCount     未命中数
     * @param evictionCount 驱逐数
     * @param size          估算大小
     */
    public record CacheStatsSnapshot(long hitCount, long missCount, long evictionCount, long size) {
    }

    /**
     * 将缓存中的对象转换为指定类型。
     *
     * @param value 缓存对象
     * @param type  目标类型
     * @param <T>   目标类型
     * @return 转换后的值
     */
    @SuppressWarnings("unchecked")
    private static <T> T convert(Object value, Class<T> type) {
        return type.isInstance(value) ? (T) value : JsonUtils.fromJson(JsonUtils.toJson(value), type);
    }

    /**
     * 物理寿命按丢弃时间算，而不是逻辑过期时间。
     * 逻辑过期后条目还得留着，降级时才有旧值可用。
     */
    private static final class EntryExpiry implements Expiry<String, CacheEntry> {

        @Override
        public long expireAfterCreate(String key, CacheEntry value, long currentTime) {
            return TimeUnit.MILLISECONDS.toNanos(Math.max(1, value.discardAtMillis() - System.currentTimeMillis()));
        }

        @Override
        public long expireAfterUpdate(String key, CacheEntry value, long currentTime, long currentDuration) {
            return expireAfterCreate(key, value, currentTime);
        }

        @Override
        public long expireAfterRead(String key, CacheEntry value, long currentTime, long currentDuration) {
            return currentDuration;
        }

    }

}
