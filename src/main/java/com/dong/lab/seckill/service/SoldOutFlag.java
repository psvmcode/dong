package com.dong.lab.seckill.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.LongAdder;

/**
 * 本地售罄标记。库存归零后在本地缓存一份标记，
 * 后续请求直接返回，连 Redis 都不用访问，这是四道防线里的第二道。
 *
 * <p>ttl 只设 10 秒，因为售罄状态可能因订单取消而回滚，
 * 标记存在越久，误拒的时间窗就越长。
 */
@Slf4j
@Service
public class SoldOutFlag {

    private static final Duration FLAG_TTL = Duration.ofSeconds(10);

    private final Cache<Long, Boolean> flags = Caffeine.newBuilder()
            .expireAfterWrite(FLAG_TTL)
            .maximumSize(10_000)
            .build();

    private final LongAdder shortCircuited = new LongAdder();

    public void mark(Long activityId) {
        flags.put(activityId, Boolean.TRUE);
    }

    public boolean isSoldOut(Long activityId) {
        if (Boolean.TRUE.equals(flags.getIfPresent(activityId))) {
            shortCircuited.increment();
            return true;
        }
        return false;
    }

    public void clear(Long activityId) {
        flags.invalidate(activityId);
    }

    public long shortCircuitedCount() {
        return shortCircuited.sum();
    }

}
