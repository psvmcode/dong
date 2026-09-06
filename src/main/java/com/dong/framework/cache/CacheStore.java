package com.dong.framework.cache;

import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;

/**
 * 缓存层抽象，L1 与 L2 共用同一接口，多级缓存因此无需区分两者。
 */
public interface CacheStore {

    /**
     * 返回缓存层名称。
     *
     * @return 名称
     */
    String name();

    /**
     * 查询。返回三态结果，Miss 与 Empty 的语义不同，不能混为一谈。
     */
    <T> CacheLookup<T> lookup(String key, Class<T> type);

    /**
     * 查询缓存值。
     */
    <T> CacheLookup<T> lookup(String key, TypeReference<T> type);

    /**
     * 放入缓存。
     */
    void put(String key, Object value, Duration ttl);

    /**
     * 放入空值占位，用于防止缓存穿透。
     */
    void putEmpty(String key, Duration ttl);

    /**
     * 清除缓存，用于缓存失效演练。
     */
    void evict(String key);

    /**
     * 估算缓存大小。
     */
    long estimatedSize();

}
