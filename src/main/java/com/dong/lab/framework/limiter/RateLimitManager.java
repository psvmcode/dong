package com.dong.lab.framework.limiter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RateLimitManager {

    private final LocalRateLimiter localRateLimiter;

    private final RedissonRateLimiter redissonRateLimiter;

    public boolean tryAcquire(String key, RateLimitRule rule, boolean distributed) {
        RateLimiter limiter = distributed ? redissonRateLimiter : localRateLimiter;
        if (!limiter.supportedAlgorithms().contains(rule.algorithm())) {
            throw new IllegalArgumentException(
                    limiter.name() + " limiter does not support " + rule.algorithm());
        }
        return limiter.tryAcquire(key, rule);
    }

}
