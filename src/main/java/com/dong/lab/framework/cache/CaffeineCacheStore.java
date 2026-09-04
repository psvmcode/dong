package com.dong.lab.framework.cache;

import com.dong.lab.common.util.JsonUtils;
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
 */
public class CaffeineCacheStore implements CacheStore {

    /**
     * Caffeine 缓存实例。
     */
    private final Cache<String, CacheEntry> cache;

    /**
     * 构造本地缓存存储。
     *
     * @param maxSize 最大条目数
     */
    public CaffeineCacheStore(long maxSize) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfter(new EntryExpiry())
                .recordStats()
                .build();
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
     * 三种结果必须区分清楚，不能只用 null 表示异常：
     * Miss 是完全没查到、Empty 是查到空值标记、Hit 是真正命中。
     * 混淆 Miss 和 Empty 会让防穿透统计失真。
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
        if (entry.expired()) {
            cache.invalidate(key);
            return new CacheLookup.Miss<>();
        }
        if (entry.value() == CacheEmpty.INSTANCE) {
            return new CacheLookup.Empty<>();
        }
        return new CacheLookup.Hit<>(convert(entry.value(), type));
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
        if (entry == null || entry.expired()) {
            return new CacheLookup.Miss<>();
        }
        if (entry.value() == CacheEmpty.INSTANCE) {
            return new CacheLookup.Empty<>();
        }
        return new CacheLookup.Hit<>(JsonUtils.fromJson(JsonUtils.toJson(entry.value()), type));
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
        cache.put(key, CacheEntry.of(value, ttl));
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
     * @param hitCount    命中数
     * @param missCount   未命中数
     * @param evictionCount 驱逐数
     * @param size        估算大小
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
     * 按 entry 自身记录的过期时间计算，而不是全局固定 ttl。
     * 这样每一条缓存都能有各自的生命周期，抖动策略才能生效。
     */
    private static final class EntryExpiry implements Expiry<String, CacheEntry> {

        @Override
        public long expireAfterCreate(String key, CacheEntry value, long currentTime) {
            return TimeUnit.MILLISECONDS.toNanos(Math.max(1, value.expireAtMillis() - System.currentTimeMillis()));
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
