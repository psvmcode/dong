package com.dong.lab.classic.service.impl;

import com.dong.lab.classic.service.RateLimitLabService;
import com.dong.lab.framework.limiter.RateLimitAlgorithm;
import com.dong.lab.framework.limiter.RateLimitManager;
import com.dong.lab.framework.limiter.RateLimitRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
/**
 * 限流算法对比实现。四种算法在同一突发流量下放行数量差异明显：
 * 固定窗口在边界处最多放过两倍配额，滑动窗口精确但占内存，
 * 令牌桶允许突发，漏桶强制匀速。
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class RateLimitLabServiceImpl implements RateLimitLabService {

    /**
     * rateLimitManager。
     */
    private final RateLimitManager rateLimitManager;

    @Override
    /**
     * 对比限流效果。
     */
    public Map<String, Object> compare(String bizKey, long limit, long windowSeconds, int attempts, boolean distributed) {
        return compare(bizKey, limit, windowSeconds, attempts, distributed, 0L);
    }

    @Override
    /**
     * 对比限流效果。
     */
    public Map<String, Object> compare(String bizKey, long limit, long windowSeconds, int attempts,
                                       boolean distributed, long delayMillis) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (RateLimitAlgorithm algorithm : RateLimitAlgorithm.values()) {
            String key = bizKey + ":" + algorithm.name().toLowerCase() + ":" + UUID.randomUUID();
            RateLimitRule rule = new RateLimitRule(limit, Duration.ofSeconds(windowSeconds), algorithm);
            long firstBurst = burst(key, rule, attempts, distributed);
            if (firstBurst < 0) {
                result.put(algorithm.name(), Map.of("supported", false));
                continue;
            }
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("firstBurstAllowed", firstBurst);
            detail.put("firstBurstRejected", attempts - firstBurst);
            if (delayMillis > 0) {
                sleep(delayMillis);
                long secondBurst = burst(key, rule, attempts, distributed);
                detail.put("secondBurstAllowed", secondBurst);
                detail.put("recoveredInGap", secondBurst);
            }
            result.put(algorithm.name(), detail);
        }
        log.info("rate limit comparison finished distributed={} limit={} attempts={} delayMillis={}",
                distributed, limit, attempts, delayMillis);
        return result;
    }

    /**
     * 连续尝试 attempts 次，返回放行数量。返回负数表示该算法不被当前实现支持。
     */
    private long burst(String key, RateLimitRule rule, int attempts, boolean distributed) {
        long allowed = 0L;
        for (int i = 0; i < attempts; i++) {
            try {
                if (rateLimitManager.tryAcquire(key, rule, distributed)) {
                    allowed++;
                }
            } catch (IllegalArgumentException ex) {
                return -1L;
            }
        }
        return allowed;
    }

    /**
     * 模拟耗时操作。
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("rate limit comparison interrupted", ex);
        }
    }

}
