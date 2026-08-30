package com.dong.lab.framework.cache;

import java.time.Duration;

/**
 * 缓存条目包装。把过期时间一起存下来，
 * 这样每条缓存可以有各自的生命周期，TTL 抖动才能生效。
 */
record CacheEntry(Object value, long expireAtMillis) {

    static CacheEntry of(Object value, Duration ttl) {
        return new CacheEntry(value, System.currentTimeMillis() + ttl.toMillis());
    }

    boolean expired() {
        return System.currentTimeMillis() > expireAtMillis;
    }

}
