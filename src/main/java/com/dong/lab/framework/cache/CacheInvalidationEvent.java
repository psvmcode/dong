package com.dong.lab.framework.cache;

public /**
 * 失效事件。带来源节点，用于过滤自己发出的广播。
 */
record CacheInvalidationEvent(String key, String sourceNode, long timestamp) {
}
