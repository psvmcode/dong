package com.dong.redpacket.support;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.framework.limiter.RateLimitAlgorithm;
import com.dong.framework.limiter.RateLimitManager;
import com.dong.framework.limiter.RateLimitRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
/**
 * 抢红包的单用户限流。挡的是"一个人用脚本刷"，与全局按 IP 限流互补：
 * 换 IP 能绕过全局限流，但绕不过按用户 id 的这个桶。
 *
 * <p>限流器本身依赖 Redis，这里刻意让它在 Redis 故障时放行而不是拒绝。
 * 拒绝等于把"Redis 抖动"放大成"整个抢红包不可用"，而放行之后
 * 还有数据库唯一索引兜着，最坏结果是慢，不是错。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrabRateLimiter {

    private static final String PREFIX = "lab:redpacket:rl:user:";

    /**
     * 限流管理器。
     */
    private final RateLimitManager rateLimitManager;

    /**
     * 运行时计数器。
     */
    private final RedPacketMetrics metrics;

    /**
     * 是否开启单用户限流。
     */
    @Value("${dong.redpacket.grab-limit-enabled:true}")
    private boolean enabled;

    /**
     * 单用户每分钟允许的抢红包次数。
     */
    @Value("${dong.redpacket.grab-limit-per-minute:60}")
    private long limitPerMinute;

    /**
     * 校验单个用户的抢红包频率，超配额抛 1003。
     *
     * @param userId 用户 id
     */
    public void check(Long userId) {
        if (!enabled) {
            return;
        }
        RateLimitRule rule = new RateLimitRule(limitPerMinute, Duration.ofMinutes(1),
                RateLimitAlgorithm.SLIDING_WINDOW);
        boolean allowed;
        try {
            allowed = rateLimitManager.tryAcquire(PREFIX + userId, rule, true);
        } catch (Exception ex) {
            metrics.rateLimitDegraded();
            log.warn("grab rate limiter unavailable, letting the request through: {}", ex.getMessage());
            return;
        }
        if (!allowed) {
            metrics.rateLimitRejected();
            throw new BusinessException(Constants.CODE_TOO_MANY_REQUESTS,
                    "grab too frequently, at most " + limitPerMinute + " times per minute");
        }
    }

}
