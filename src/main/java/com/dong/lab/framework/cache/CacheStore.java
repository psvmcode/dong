package com.dong.lab.framework.cache;

import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;

/**
 * 缓存层抽象，L1 与 L2 共用同一接口，多级缓存因此无需区分两者。
 */
public interface CacheStore {

    String name();

    /**
     * 查询。返回三态结果，Miss 与 Empty 的语义不同，不能混为一谈。
     */
    <T> CacheLookup<T> lookup(String key, Class<T> type);

    <T> CacheLookup<T> lookup(String key, TypeReference<T> type);

    void put(String key, Object value, Duration ttl);

    void putEmpty(String key, Duration ttl);

    void evict(String key);

    long estimatedSize();

}
