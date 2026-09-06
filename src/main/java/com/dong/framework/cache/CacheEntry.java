package com.dong.framework.cache;

import java.time.Duration;

/**
 * 缓存条目包装。把过期时间一起存下来，
 * 这样每条缓存可以有各自的生命周期，TTL 抖动才能生效。
 *
 * <p>放在包外可见，是因为具体实现已经移到 impl 子包，
 * 两级缓存都要读写同一个条目类型。
 */
public record CacheEntry(Object value, long expireAtMillis) {

    public static CacheEntry of(Object value, Duration ttl) {
        return new CacheEntry(value, System.currentTimeMillis() + ttl.toMillis());
    }

    public boolean expired() {
        return System.currentTimeMillis() > expireAtMillis;
    }

}
