package com.dong.lab.framework.cache;

import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;

public interface CacheStore {

    String name();

    <T> CacheLookup<T> lookup(String key, Class<T> type);

    <T> CacheLookup<T> lookup(String key, TypeReference<T> type);

    void put(String key, Object value, Duration ttl);

    void putEmpty(String key, Duration ttl);

    void evict(String key);

    long estimatedSize();

}
