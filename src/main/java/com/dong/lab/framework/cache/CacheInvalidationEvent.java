package com.dong.lab.framework.cache;

public record CacheInvalidationEvent(String key, String sourceNode, long timestamp) {
}
