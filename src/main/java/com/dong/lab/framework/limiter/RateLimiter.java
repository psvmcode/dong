package com.dong.lab.framework.limiter;

import java.util.Set;

public interface RateLimiter {

    boolean tryAcquire(String key, RateLimitRule rule, long permits);

    default boolean tryAcquire(String key, RateLimitRule rule) {
        return tryAcquire(key, rule, 1L);
    }

    Set<RateLimitAlgorithm> supportedAlgorithms();

    String name();

}
