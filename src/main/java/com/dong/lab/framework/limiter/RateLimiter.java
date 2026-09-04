package com.dong.lab.framework.limiter;

import java.util.Set;

/**
 * 限流器抽象。本地与分布式两种实现共用这一接口，
 * 调用方通过 supportedAlgorithms 判断能否处理指定算法。
 */
public interface RateLimiter {

    /**
     * 尝试获取 permits 个配额。
     *
     * @param key    业务键
     * @param rule   限流规则
     * @param permits 请求配额
     * @return 是否放行
     */
    boolean tryAcquire(String key, RateLimitRule rule, long permits);

    /**
     * 尝试获取 1 个配额。
     *
     * @param key  业务键
     * @param rule 限流规则
     * @return 是否放行
     */
    default boolean tryAcquire(String key, RateLimitRule rule) {
        return tryAcquire(key, rule, 1L);
    }

    /**
     * 该实现支持的算法集合，不支持的算法不应静默降级。
     *
     * @return 支持的算法集合
     */
    Set<RateLimitAlgorithm> supportedAlgorithms();

    /**
     * 返回限流器名称。
     *
     * @return 名称
     */
    String name();

}
