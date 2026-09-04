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

    /**
     * 本地限流器实现。
     */
    private final LocalRateLimiter localRateLimiter;

    /**
     * Lua 分布式限流器实现。
     */
    private final LuaRateLimiter luaRateLimiter;

    /**
     * 尝试获取配额。根据 distributed 选择本地或分布式实现，
     * 不支持的算法会直接抛异常而不是静默放行。
     *
     * @param key         业务键
     * @param rule        限流规则
     * @param distributed 是否分布式
     * @return 是否放行
     */
    public boolean tryAcquire(String key, RateLimitRule rule, boolean distributed) {
        RateLimiter limiter = distributed ? luaRateLimiter : localRateLimiter;
        if (!limiter.supportedAlgorithms().contains(rule.algorithm())) {
            throw new IllegalArgumentException(
                    limiter.name() + " limiter does not support " + rule.algorithm());
        }
        return limiter.tryAcquire(key, rule);
    }

}
