package com.dong.lab.redpacket.service.impl;

import com.dong.lab.framework.redis.RedisService;
import com.dong.lab.redpacket.service.RedPacketStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedPacketStockServiceImpl implements RedPacketStockService {

    private static final String COUNT = "lab:redpacket:count:";

    private static final String AMOUNT = "lab:redpacket:amount:";

    private static final String LIST = "lab:redpacket:list:";

    private static final String USERS = "lab:redpacket:users:";

    private static final Duration TTL = Duration.ofHours(24);

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

    private final RedisService redisService;

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

    @Override
    public long grab(String packetNo, Long userId) {
        Long result = redisService.executeToLong(GRAB,
                List.of(COUNT + packetNo, AMOUNT + packetNo, LIST + packetNo, USERS + packetNo), userId);
        return result == null ? -1L : result;
    }

    @Override
    public int remainCount(String packetNo) {
        return Integer.parseInt(redisService.get(COUNT + packetNo).orElse("0"));
    }

    @Override
    public long remainAmount(String packetNo) {
        return Long.parseLong(redisService.get(AMOUNT + packetNo).orElse("0"));
    }

}
