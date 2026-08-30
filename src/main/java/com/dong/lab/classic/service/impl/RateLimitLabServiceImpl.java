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

    private final RateLimitManager rateLimitManager;

    @Override
    public Map<String, Object> compare(String bizKey, long limit, long windowSeconds, int attempts, boolean distributed) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (RateLimitAlgorithm algorithm : RateLimitAlgorithm.values()) {
            String key = bizKey + ":" + algorithm.name().toLowerCase() + ":" + UUID.randomUUID();
            RateLimitRule rule = new RateLimitRule(limit, Duration.ofSeconds(windowSeconds), algorithm);
            long allowed = 0L;
            boolean supported = true;
            for (int i = 0; i < attempts; i++) {
                try {
                    if (rateLimitManager.tryAcquire(key, rule, distributed)) {
                        allowed++;
                    }
                } catch (IllegalArgumentException ex) {
                    supported = false;
                    break;
                }
            }
            if (supported) {
                result.put(algorithm.name(), Map.of("allowed", allowed, "rejected", attempts - allowed));
            } else {
                result.put(algorithm.name(), Map.of("allowed", 0, "rejected", 0, "supported", false));
            }
        }
        log.info("rate limit comparison finished distributed={} limit={} attempts={}", distributed, limit, attempts);
        return result;
    }

}
