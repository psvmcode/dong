package com.dong.lab.framework.limiter;

import java.util.Set;

/**
 * 限流器抽象。本地与分布式两种实现共用这一接口，
 * 调用方通过 supportedAlgorithms 判断能否处理指定算法。
 */
public interface RateLimiter {

    /**
     * 尝试获取 permits 个配额。
     */
    boolean tryAcquire(String key, RateLimitRule rule, long permits);

    default boolean tryAcquire(String key, RateLimitRule rule) {
        return tryAcquire(key, rule, 1L);
    }

    /**
     * 该实现支持的算法集合，不支持的算法不应静默降级。
     */
    Set<RateLimitAlgorithm> supportedAlgorithms();

    String name();

}
