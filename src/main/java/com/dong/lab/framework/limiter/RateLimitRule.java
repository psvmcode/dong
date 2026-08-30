package com.dong.lab.framework.limiter;

import java.time.Duration;

/**
 * 限流规则。三者共同决定配额：窗口内最多放行 limit 次，按 algorithm 的算法判定。
 */
public record RateLimitRule(long limit, Duration window, RateLimitAlgorithm algorithm) {

    public static RateLimitRule perSecond(long limit, RateLimitAlgorithm algorithm) {
        return new RateLimitRule(limit, Duration.ofSeconds(1), algorithm);
    }

    public static RateLimitRule perMinute(long limit, RateLimitAlgorithm algorithm) {
        return new RateLimitRule(limit, Duration.ofMinutes(1), algorithm);
    }

}
