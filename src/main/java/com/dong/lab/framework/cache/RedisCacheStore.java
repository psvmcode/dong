package com.dong.lab.framework.cache;

import com.dong.lab.common.util.JsonUtils;
import com.dong.lab.framework.redis.RedisService;
import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

public class RedisCacheStore implements CacheStore {

    private static final String KEY_PREFIX = "lab:cache:";

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
