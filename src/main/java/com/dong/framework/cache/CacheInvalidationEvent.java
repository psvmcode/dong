package com.dong.framework.cache;

/**
 * 失效事件。带来源节点，用于过滤自己发出的广播。
 *
 * @param key        缓存键
 * @param sourceNode 来源节点
 * @param timestamp  时间戳
 */
public record CacheInvalidationEvent(String key, String sourceNode, long timestamp) {
}
