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

/**
 * 分布式限流器，基于 Redisson 的 RRateLimiter，计数在 Redis 上全局共享。
 *
 * <p><b>已知限制</b>：Redisson 的 RRateLimiter 底层只有一种令牌桶实现，
 * 只能选择全局或按客户端计数。本类的 toRateType 对四种算法都返回
 * {@code RateType.OVERALL}，因此在分布式模式下四种算法的放行结果完全相同，
 * 对比实验失去意义。要看真实差异请用 distributed=false，
 * 或改用 Lua 脚本自行实现各算法。
 */
@Component
@RequiredArgsConstructor
public class RedissonRateLimiter implements RateLimiter {

    private static final String KEY_PREFIX = "lab:limiter:";

    private final RedissonClient redissonClient;

    // 本地缓存已初始化过的 key，避免每次请求都调用 trySetRate
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

    /**
     * 注意：两个分支当前都是 OVERALL，导致四种算法行为一致。
     * Redisson 原生不区分窗口与漏桶，如需真实差异必须自行实现。
     */
    private RateType toRateType(RateLimitAlgorithm algorithm) {
        return algorithm == RateLimitAlgorithm.TOKEN_BUCKET ? RateType.OVERALL : RateType.OVERALL;
    }

    private long intervalOf(Duration window) {
        long millis = window.toMillis();
        return millis < 1L ? 1L : millis;
    }

}
