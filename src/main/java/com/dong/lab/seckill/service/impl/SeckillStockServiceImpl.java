package com.dong.lab.seckill.service.impl;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import com.dong.lab.framework.redis.RedisService;
import com.dong.lab.seckill.service.SeckillStockService;
import com.dong.lab.seckill.service.SoldOutFlag;
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
public class SeckillStockServiceImpl implements SeckillStockService {

    private static final String STOCK = "lab:seckill:stock:";

    private static final String PARTICIPANTS = "lab:seckill:users:";

    private static final Duration STOCK_TTL = Duration.ofHours(24);

    private static final String DEDUCT_SCRIPT = """
            if redis.call('sismember', KEYS[2], ARGV[2]) == 1 then
                return -3
            end

            local stock = tonumber(redis.call('get', KEYS[1]))
            if stock == nil then
                return -1
            end

            local quantity = tonumber(ARGV[1])
            if stock < quantity then
                return -2
            end

            redis.call('decrby', KEYS[1], quantity)
            redis.call('sadd', KEYS[2], ARGV[2])
            return stock - quantity
            """;

    private static final RedisScript<Long> DEDUCT = new DefaultRedisScript<>(DEDUCT_SCRIPT, Long.class);

    private final RedisService redisService;

    private final SoldOutFlag soldOutFlag;

    @Override
    public void prepare(Long activityId, int totalStock) {
        redisService.set(stockKey(activityId), String.valueOf(totalStock), STOCK_TTL);
        redisService.delete(participantsKey(activityId));
        log.info("seckill stock prepared activity={} stock={}", activityId, totalStock);
    }

    @Override
    public int deduct(Long activityId, Long userId, int quantity) {
        Long result = redisService.execute(DEDUCT,
                List.of(stockKey(activityId), participantsKey(activityId)), quantity, userId);
        if (result == null) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE,
                    "seckill deduct script returned nothing");
        }
        return result.intValue();
    }

    @Override
    public int rollback(Long activityId, Long userId, int quantity) {
        redisService.template().opsForSet().remove(participantsKey(activityId), userId.toString());
        Long remaining = redisService.incrementBy(stockKey(activityId), quantity);
        soldOutFlag.clear(activityId);
        log.info("seckill stock rolled back activity={} user={} quantity={}", activityId, userId, quantity);
        return remaining == null ? 0 : remaining.intValue();
    }

    @Override
    public int available(Long activityId) {
        return Integer.parseInt(redisService.get(stockKey(activityId)).orElse("-1"));
    }

    @Override
    public void clear(Long activityId) {
        redisService.delete(List.of(stockKey(activityId), participantsKey(activityId)));
    }

    private String stockKey(Long activityId) {
        return STOCK + activityId;
    }

    private String participantsKey(Long activityId) {
        return PARTICIPANTS + activityId;
    }

}
