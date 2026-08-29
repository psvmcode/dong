package com.dong.lab.framework.cache;

import java.time.Duration;

record CacheEntry(Object value, long expireAtMillis) {

    static CacheEntry of(Object value, Duration ttl) {
        return new CacheEntry(value, System.currentTimeMillis() + ttl.toMillis());
    }

    boolean expired() {
        return System.currentTimeMillis() > expireAtMillis;
    }

}
