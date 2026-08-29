package com.dong.lab.framework.cache;

import com.dong.lab.common.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class CaffeineCacheStore implements CacheStore {

    private final Cache<String, CacheEntry> cache;

    public CaffeineCacheStore(long maxSize) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfter(new EntryExpiry())
                .recordStats()
                .build();
    }

    @Override
    public String name() {
        return "caffeine";
    }

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

    @Override
    public void put(String key, Object value, Duration ttl) {
        cache.put(key, CacheEntry.of(value, ttl));
    }

    @Override
    public void putEmpty(String key, Duration ttl) {
        cache.put(key, CacheEntry.of(CacheEmpty.INSTANCE, ttl));
    }

    @Override
    public void evict(String key) {
        cache.invalidate(key);
    }

    @Override
    public long estimatedSize() {
        return cache.estimatedSize();
    }

    public void clear() {
        cache.invalidateAll();
    }

    public CacheStatsSnapshot snapshot() {
        var stats = cache.stats();
        return new CacheStatsSnapshot(stats.hitCount(), stats.missCount(), stats.evictionCount(), cache.estimatedSize());
    }

    public record CacheStatsSnapshot(long hitCount, long missCount, long evictionCount, long size) {
    }

    @SuppressWarnings("unchecked")
    private static <T> T convert(Object value, Class<T> type) {
        return type.isInstance(value) ? (T) value : JsonUtils.fromJson(JsonUtils.toJson(value), type);
    }

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
