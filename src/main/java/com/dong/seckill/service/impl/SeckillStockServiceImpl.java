package com.dong.seckill.service.impl;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.framework.redis.RedisService;
import com.dong.seckill.service.SeckillStockService;
import com.dong.seckill.support.SoldOutFlag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 秒杀库存实现。查余额、扣减、记录用户三步由一条 Lua 脚本完成，
 * 原子性由 Redis 单线程保证，因此不需要分布式锁，也不存在读改写竞态。
 *
 * <p>踩过的坑：脚本返回值曾经被错误解码，导致库存已扣却判定失败，
 * 库存凭空消失。所以这里对 null 一律抛错，绝不静默当成成功。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillStockServiceImpl implements SeckillStockService {

    private static final String STOCK = "lab:seckill:stock:";

    private static final String PARTICIPANTS = "lab:seckill:users:";

    private static final Duration STOCK_TTL = Duration.ofHours(24);

    /**
     * 返回值约定：负数表示各类失败，非负数表示扣减后的剩余库存。
     * -1 未预热、-2 库存不足、-3 重复购买。
     */
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

    /**
     * 回滚库存。库存键只在仍然存在时才回加：键过期后 INCRBY 会以零起底
     * 重建一个无过期时间的「幽灵库存」，让早已结束的活动重新显示出可售。
     * 回加与移除参与者放同一条脚本，消除两步之间的重复购买窗口。
     */
    private static final String ROLLBACK_SCRIPT = """
            if redis.call('exists', KEYS[1]) == 1 then
                redis.call('incrby', KEYS[1], tonumber(ARGV[1]))
            end
            redis.call('srem', KEYS[2], ARGV[2])
            return 1
            """;

    private static final RedisScript<Long> ROLLBACK = new DefaultRedisScript<>(ROLLBACK_SCRIPT, Long.class);

    /**
     * redisService，业务服务层。
     */
    private final RedisService redisService;

    /**
     * 本地售罄标记，库存归零后短路后续请求。
     */
    private final SoldOutFlag soldOutFlag;

    /**
     * 预热库存到 Redis。
     */
    @Override
    public void prepare(Long activityId, int totalStock) {
        redisService.set(stockKey(activityId), String.valueOf(totalStock), STOCK_TTL);
        redisService.delete(participantsKey(activityId));
        log.info("seckill stock prepared activity={} stock={}", activityId, totalStock);
    }

    /**
     * 扣减库存，返回扣减后的剩余库存或负数表示失败。
     */
    @Override
    public int deduct(Long activityId, Long userId, int quantity) {
        Long result = redisService.execute(DEDUCT, List.of(stockKey(activityId), participantsKey(activityId)), quantity, userId);
        if (result == null) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "seckill deduct script returned nothing");
        }
        return result.intValue();
    }

    /**
     * 回滚库存。必须同时清掉售罄标记，
     * 否则库存虽然回来了，后续请求仍会被本地标记直接拒绝。
     */
    @Override
    public int rollback(Long activityId, Long userId, int quantity) {
        redisService.execute(ROLLBACK, List.of(stockKey(activityId), participantsKey(activityId)), quantity, userId);
        soldOutFlag.clear(activityId);
        log.info("seckill stock rolled back activity={} user={} quantity={}", activityId, userId, quantity);
        return available(activityId);
    }

    /**
     * 查询剩余库存。
     */
    @Override
    public int available(Long activityId) {
        String value = redisService.get(stockKey(activityId)).orElse("-1");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            // 库存键被写脏时不能让补偿与超时回收任务整体失败，-1 走「未预热」分支
            log.warn("invalid seckill stock value activity={} value={}", activityId, value);
            return -1;
        }
    }

    /**
     * 清空库存记录。
     */
    @Override
    public void clear(Long activityId) {
        redisService.delete(List.of(stockKey(activityId), participantsKey(activityId)));
    }

    /**
     * 构造库存 Redis Key。
     */
    private String stockKey(Long activityId) {
        return STOCK + activityId;
    }

    /**
     * 构造参与者集合 Redis Key。
     */
    private String participantsKey(Long activityId) {
        return PARTICIPANTS + activityId;
    }

}
