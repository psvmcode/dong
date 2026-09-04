package com.dong.lab.framework.limiter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 * 本地限流器。状态存在进程内的 Caffeine 里，因此只在单节点内有效，
 * 多实例部署时每个节点各自计数，实际放放量是单节点配额乘以节点数。
 *
 * <p>只实现了令牌桶与固定窗口两种，滑动窗口与漏桶需要 Redisson 实现。
 */
@Component

public class LocalRateLimiter implements RateLimiter {

    // 空闲 key 十分钟后清理，避免长期不访问的 key 白占内存
    private static final Duration IDLE_EVICTION = Duration.ofMinutes(10);

    private static final long MAX_TRACKED_KEYS = 100_000L;

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(IDLE_EVICTION)
            .maximumSize(MAX_TRACKED_KEYS)
            .build();

    @Override
    /**
     * tryAcquire。
     */
    public boolean tryAcquire(String key, RateLimitRule rule, long permits) {
        return switch (rule.algorithm()) {
            case TOKEN_BUCKET -> acquireTokenBucket(key, rule, permits);
            case FIXED_WINDOW -> acquireFixedWindow(key, rule);
            default -> throw new IllegalArgumentException(
                    "local limiter does not support " + rule.algorithm() + ", use the redisson limiter");
        };
    }

    @Override
    /**
     * supportedAlgorithms。
     */
    public Set<RateLimitAlgorithm> supportedAlgorithms() {
        return Set.of(RateLimitAlgorithm.TOKEN_BUCKET, RateLimitAlgorithm.FIXED_WINDOW);
    }

    @Override
    /**
     * name。
     */
    public String name() {
        return "local";
    }

    /**
     * 令牌桶。按时间比例匀速补充令牌，桶内有多少令牌就允许通过多少，
     * 因此允许突发流量，只要桶里有存货。
     */
    private boolean acquireTokenBucket(String key, RateLimitRule rule, long permits) {
        double refillPerNanos = (double) rule.limit() / rule.window().toNanos();
        long now = System.nanoTime();
        AtomicBoolean allowed = new AtomicBoolean(false);
        buckets.asMap().compute(key, (ignored, existing) -> {
            Bucket bucket = existing == null ? new Bucket(rule.limit(), now) : existing;
            bucket.refill(refillPerNanos, rule.limit(), now);
            if (bucket.tokens >= permits) {
                bucket.tokens = bucket.tokens - permits;
                allowed.set(true);
            }
            return bucket;
        });
        return allowed.get();
    }

    /**
     * 固定窗口。按当前时间整除窗口长度得到窗口序号，窗口内计数。
     * 缺陷在于窗口边界：上一个窗口末尾与下一个窗口开头连起来的极短时间内，
     * 最多可以放过两倍配额。
     */
    private boolean acquireFixedWindow(String key, RateLimitRule rule) {
        long windowIndex = System.currentTimeMillis() / rule.window().toMillis();
        String windowKey = key + ":" + windowIndex;
        AtomicBoolean allowed = new AtomicBoolean(false);
        buckets.asMap().compute(windowKey, (ignored, existing) -> {
            Bucket bucket = existing == null ? new Bucket(0, System.nanoTime()) : existing;
            if (bucket.tokens < rule.limit()) {
                bucket.tokens = bucket.tokens + 1;
                allowed.set(true);
            }
            return bucket;
        });
        return allowed.get();
    }

    private static final class Bucket {
    /**
     * tokens。
     */
        private double tokens;

    /**
     * lastRefillNanos。
     */
        private long lastRefillNanos;
        private Bucket(double tokens, long lastRefillNanos) {
            this.tokens = tokens;
            this.lastRefillNanos = lastRefillNanos;
        }

        private void refill(double refillPerNanos, double capacity, long now) {
            long elapsed = Math.max(0, now - lastRefillNanos);
            tokens = Math.min(capacity, tokens + elapsed * refillPerNanos);
            lastRefillNanos = now;
        }

    }

}
