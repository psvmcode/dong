package com.dong.framework.cache;

import java.time.Duration;

/**
 * 缓存条目包装。同时记录逻辑过期时间与物理丢弃时间，
 * 这样每条缓存可以有各自的生命周期，TTL 抖动才能生效。
 *
 * <p>逻辑过期之后数据还要再留一段宽容期：
 * 这段时间里它不再算命中，但回源失败时可以作为兜底值返回，
 * 让「数据库抖一下」表现为「数据稍旧」而不是「服务不可用」。
 *
 * <p>放在包外可见，是因为具体实现已经移到 impl 子包，
 * 两级缓存都要读写同一个条目类型。
 */
public record CacheEntry(Object value, long expireAtMillis, long discardAtMillis) {

    /**
     * 按 ttl 构造条目，不带宽容期，逻辑过期即可丢弃。
     *
     * @param value 缓存值
     * @param ttl   存活时间
     * @return 缓存条目
     */
    public static CacheEntry of(Object value, Duration ttl) {
        return of(value, ttl, Duration.ZERO);
    }

    /**
     * 按 ttl 与宽容期构造条目。
     *
     * @param value 缓存值
     * @param ttl   逻辑存活时间
     * @param grace 逻辑过期后仍保留的时间，用于降级兜底
     * @return 缓存条目
     */
    public static CacheEntry of(Object value, Duration ttl, Duration grace) {
        long expireAt = System.currentTimeMillis() + ttl.toMillis();
        return new CacheEntry(value, expireAt, expireAt + grace.toMillis());
    }

    /**
     * 逻辑过期。过期后不能再当命中返回，但值还在。
     *
     * @return 是否已逻辑过期
     */
    public boolean expired() {
        return System.currentTimeMillis() > expireAtMillis;
    }

    /**
     * 物理过期。到点后条目必须彻底丢弃，连兜底也不能用。
     *
     * @return 是否应丢弃
     */
    public boolean discarded() {
        return System.currentTimeMillis() > discardAtMillis;
    }

}
