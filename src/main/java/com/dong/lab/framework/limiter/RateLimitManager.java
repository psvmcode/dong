package com.dong.lab.framework.limiter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 限流入口。根据 distributed 选择分布式或本地实现，
 * 并在调用前校验算法是否被该实现支持。
 */
@Component
@RequiredArgsConstructor
public class RateLimitManager {

    private final LocalRateLimiter localRateLimiter;

    private final RedissonRateLimiter redissonRateLimiter;

    /**
     * 注意本地实现只支持部分算法，传了不支持的算法会直接抛异常而不是放行，
     * 这样能让问题尽早暴露。
     */
    public boolean tryAcquire(String key, RateLimitRule rule, boolean distributed) {
        RateLimiter limiter = distributed ? redissonRateLimiter : localRateLimiter;
        if (!limiter.supportedAlgorithms().contains(rule.algorithm())) {
            throw new IllegalArgumentException(
                    limiter.name() + " limiter does not support " + rule.algorithm());
        }
        return limiter.tryAcquire(key, rule);
    }

}
