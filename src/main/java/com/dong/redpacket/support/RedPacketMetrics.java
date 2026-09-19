package com.dong.redpacket.support;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
/**
 * 抢红包的运行时计数器。降级、重建、补偿这些动作平时不可见，
 * 没有计数就只能等用户投诉，有了计数才能判断一次故障到底造成了多大范围的降级。
 */
@Component
public class RedPacketMetrics {

    private final LongAdder rateLimitRejected = new LongAdder();

    private final LongAdder rateLimitDegraded = new LongAdder();

    private final LongAdder redisUnavailable = new LongAdder();

    private final LongAdder dbFallback = new LongAdder();

    private final LongAdder stockRebuilt = new LongAdder();

    private final LongAdder stockRestored = new LongAdder();

    private final LongAdder staleShare = new LongAdder();

    /**
     * 记录一次被单用户限流拒绝。
     */
    public void rateLimitRejected() {
        rateLimitRejected.increment();
    }

    /**
     * 记录一次限流器自身不可用而放行。
     */
    public void rateLimitDegraded() {
        rateLimitDegraded.increment();
    }

    /**
     * 记录一次 Redis 库存不可用。
     */
    public void redisUnavailable() {
        redisUnavailable.increment();
    }

    /**
     * 记录一次降级到数据库抢红包。
     */
    public void dbFallback() {
        dbFallback.increment();
    }

    /**
     * 记录一次从数据库重建 Redis 库存。
     */
    public void stockRebuilt() {
        stockRebuilt.increment();
    }

    /**
     * 记录一次库存归还补偿。
     */
    public void stockRestored() {
        stockRestored.increment();
    }

    /**
     * 记录一次丢弃脏份额。
     */
    public void staleShare() {
        staleShare.increment();
    }

    /**
     * 导出当前计数值。
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("rateLimitRejected", rateLimitRejected.sum());
        snapshot.put("rateLimitDegraded", rateLimitDegraded.sum());
        snapshot.put("redisUnavailable", redisUnavailable.sum());
        snapshot.put("dbFallback", dbFallback.sum());
        snapshot.put("stockRebuilt", stockRebuilt.sum());
        snapshot.put("stockRestored", stockRestored.sum());
        snapshot.put("staleShare", staleShare.sum());
        return snapshot;
    }

}
