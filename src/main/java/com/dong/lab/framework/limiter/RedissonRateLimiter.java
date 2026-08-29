package com.dong.lab.framework.limiter;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class RedissonRateLimiter implements RateLimiter {

    private static final String KEY_PREFIX = "lab:limiter:";

    private final RedissonClient redissonClient;

    private final ConcurrentHashMap<String, Boolean> initialised = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, RateLimitRule rule, long permits) {
        RRateLimiter limiter = limiterOf(key, rule);
        return limiter.tryAcquire(permits);
    }

    @Override
    public Set<RateLimitAlgorithm> supportedAlgorithms() {
        return Set.of(RateLimitAlgorithm.values());
    }

    @Override
    public String name() {
        return "redisson";
    }

    private RRateLimiter limiterOf(String key, RateLimitRule rule) {
        String redisKey = KEY_PREFIX + key + ":" + rule.algorithm().name().toLowerCase()
                + ":" + rule.limit() + ":" + rule.window().toMillis();
        RRateLimiter limiter = redissonClient.getRateLimiter(redisKey);
        initialised.computeIfAbsent(redisKey, ignored -> {
            limiter.trySetRate(toRateType(rule.algorithm()), rule.limit(),
                    intervalOf(rule.window()), RateIntervalUnit.MILLISECONDS);
            return Boolean.TRUE;
        });
        return limiter;
    }

    private RateType toRateType(RateLimitAlgorithm algorithm) {
        return algorithm == RateLimitAlgorithm.TOKEN_BUCKET ? RateType.OVERALL : RateType.OVERALL;
    }

    private long intervalOf(Duration window) {
        long millis = window.toMillis();
        return millis < 1L ? 1L : millis;
    }

}
