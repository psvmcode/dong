package com.dong.redpacket.service.impl;

import com.dong.framework.redis.RedisService;
import com.dong.redpacket.service.RedPacketStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
/**
 * 红包库存实现。金额在发红包时就预分配好并整体推入 List，
 * 抢的时候只是一次 RPOP，原子性由 Redis 单线程保证，全程无锁无事务。
 *
 * <p>这样设计的好处是再多人并发抢也不会竞争，
 * 且金额总和一定精确守恒，因为分配时在整数域就算好了。
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class RedPacketStockServiceImpl implements RedPacketStockService {

    private static final String COUNT = "lab:redpacket:count:";

    private static final String AMOUNT = "lab:redpacket:amount:";

    private static final String LIST = "lab:redpacket:list:";

    private static final String USERS = "lab:redpacket:users:";

    private static final Duration TTL = Duration.ofHours(24);

    /**
     * 抢红包脚本。先查重再检查余量，最后才弹出，三步必须原子完成。
     * 返回 -1 表示已抢完或重复抢，否则返回抢到的金额。
     */
    private static final String GRAB_SCRIPT = """
            if redis.call('sismember', KEYS[4], ARGV[1]) == 1 then
                return -1
            end

            local remainCount = tonumber(redis.call('get', KEYS[1]))
            if remainCount == nil or remainCount <= 0 then
                return -1
            end

            local amountStr = redis.call('rpop', KEYS[3])
            if not amountStr then
                return -1
            end

            local amount = tonumber(amountStr)
            redis.call('decr', KEYS[1])
            redis.call('decrby', KEYS[2], amount)
            redis.call('sadd', KEYS[4], ARGV[1])
            return amount
            """;

    private static final RedisScript<Long> GRAB = new DefaultRedisScript<>(GRAB_SCRIPT, Long.class);

    /**
     * redisService，业务服务层。
     */
    private final RedisService redisService;

    /**
     * 预分配红包库存，将金额列表整体推入 Redis。
     */
    @Override
    public void prepare(String packetNo, List<Long> amounts, long totalAmount) {
        redisService.delete(List.of(COUNT + packetNo, AMOUNT + packetNo, LIST + packetNo, USERS + packetNo));
        redisService.set(COUNT + packetNo, String.valueOf(amounts.size()), TTL);
        redisService.set(AMOUNT + packetNo, String.valueOf(totalAmount), TTL);
        redisService.template().opsForList().rightPushAll(LIST + packetNo,
                amounts.stream().map(String::valueOf).toList());
        redisService.expire(LIST + packetNo, TTL);
        log.info("red packet stock prepared packetNo={} count={} total={}", packetNo, amounts.size(), totalAmount);
    }

    /**
     * 抢一份红包，原子弹出并返回金额，已抢完或重复抢返回 -1。
     */
    @Override
    public long grab(String packetNo, Long userId) {
        Long result = redisService.executeToLong(GRAB,
                List.of(COUNT + packetNo, AMOUNT + packetNo, LIST + packetNo, USERS + packetNo), userId);
        return result == null ? -1L : result;
    }

    /**
     * 查询红包剩余份数。
     */
    @Override
    public int remainCount(String packetNo) {
        return Integer.parseInt(redisService.get(COUNT + packetNo).orElse("0"));
    }

    /**
     * 查询红包剩余金额。
     */
    @Override
    public long remainAmount(String packetNo) {
        return Long.parseLong(redisService.get(AMOUNT + packetNo).orElse("0"));
    }

}
