package com.dong.redpacket.support;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.LongAdder;

/**
 * 本地抢完标记。红包抢完后在本进程缓存一份标记，
 * 后续请求不必再访问 Redis，这是并发量上来后最廉价的一级卸载。
 *
 * <p>ttl 只设 10 秒：库存可能因归还补偿或重建而重新出现，
 * 标记存得越久，误拒的时间窗越长。
 */
@Slf4j
@Component
public class RedPacketSoldOutFlag {

    private static final Duration FLAG_TTL = Duration.ofSeconds(10);

    private final Cache<String, Boolean> flags = Caffeine.newBuilder().expireAfterWrite(FLAG_TTL).maximumSize(10_000).build();

    private final LongAdder shortCircuited = new LongAdder();

    /**
     * 标记某红包已抢完。
     */
    public void mark(String packetNo) {
        flags.put(packetNo, Boolean.TRUE);
    }

    /**
     * 判断某红包是否已被标记为抢完。
     */
    public boolean isSoldOut(String packetNo) {
        if (Boolean.TRUE.equals(flags.getIfPresent(packetNo))) {
            shortCircuited.increment();
            return true;
        }
        return false;
    }

    /**
     * 清除某红包的抢完标记。
     */
    public void clear(String packetNo) {
        flags.invalidate(packetNo);
    }

    /**
     * 获取被本地标记直接拦截的请求数。
     */
    public long shortCircuitedCount() {
        return shortCircuited.sum();
    }

}
