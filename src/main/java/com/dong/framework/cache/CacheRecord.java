package com.dong.framework.cache;

import java.time.Duration;

/**
 * L2 的落库形态：值以 JSON 字符串原样保存，另外记录逻辑过期与物理丢弃时间。
 *
 * <p>为什么不复用 {@link CacheEntry}：L1 存的是对象引用，直接当目标类型用即可；
 * 而 L2 取回来必然是字符串，如果先把字符串解析成通用对象、再序列化回字符串、
 * 最后才反序列化成目标类型，就多出一次完整序列化和一个中间对象。
 * 缓存命中是最热的路径，这一次往返的开销会被放大到每一次读上。
 *
 * <p>因此 L2 走「写入时序列化一次、读取时反序列化一次」：
 * 值保持原始 JSON 字符串，只在解码时才按目标类型解析。
 */
public record CacheRecord(String value, long expireAtMillis, long discardAtMillis) {

    /**
     * 按逻辑存活时间与宽容期构造记录。
     *
     * @param value  已序列化的 JSON 字符串
     * @param ttl    逻辑存活时间
     * @param grace  逻辑过期后仍保留的时间，用于降级兜底
     * @return 缓存记录
     */
    public static CacheRecord of(String value, Duration ttl, Duration grace) {
        long expireAt = System.currentTimeMillis() + ttl.toMillis();
        return new CacheRecord(value, expireAt, expireAt + grace.toMillis());
    }

    /**
     * 逻辑过期。过期后不算命中，但值还在，可作降级兜底。
     *
     * @return 是否已逻辑过期
     */
    public boolean expired() {
        return System.currentTimeMillis() > expireAtMillis;
    }

    /**
     * 物理过期。到点后必须彻底丢弃。
     *
     * @return 是否应丢弃
     */
    public boolean discarded() {
        return System.currentTimeMillis() > discardAtMillis;
    }

}
