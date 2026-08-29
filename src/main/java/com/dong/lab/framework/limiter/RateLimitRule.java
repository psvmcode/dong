package com.dong.lab.framework.limiter;

import java.time.Duration;

public record RateLimitRule(long limit, Duration window, RateLimitAlgorithm algorithm) {

    public static RateLimitRule perSecond(long limit, RateLimitAlgorithm algorithm) {
        return new RateLimitRule(limit, Duration.ofSeconds(1), algorithm);
    }

    public static RateLimitRule perMinute(long limit, RateLimitAlgorithm algorithm) {
        return new RateLimitRule(limit, Duration.ofMinutes(1), algorithm);
    }

}
