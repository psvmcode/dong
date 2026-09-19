package com.dong.redpacket.service.impl;

import com.dong.framework.redis.RedisService;
import com.dong.redpacket.dto.GrabReservation;
import com.dong.redpacket.entity.RedPacketItem;
import com.dong.redpacket.enums.GrabStatus;
import com.dong.redpacket.service.RedPacketStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
/**
 * 红包库存实现。待发队列里存的是"序号:金额"，
 * 弹出即得到一份确定金额的份额，数据库侧再用序号做一次条件更新占位。
 *
 * <p>队列只是副本，所以脚本必须能分辨三种空：
 * 库存键不存在（未预热，可重建）、计数为零（真的抢完）、
 * 计数不为零却弹不出来（副本与计数不一致，同样需要重建）。
 * 混为一谈就会出现"抢完"和"库存丢了"分不清的问题。
 *
 * <p>Redis 访问一律吞掉 DataAccessException 转成状态返回：
 * 库存层不该替业务决定要不要失败，降级与否由上层按开关判断。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedPacketStockServiceImpl implements RedPacketStockService {

    private static final String COUNT = "lab:redpacket:count:";

    private static final String AMOUNT = "lab:redpacket:amount:";

    private static final String LIST = "lab:redpacket:list:";

    private static final String USERS = "lab:redpacket:users:";

    private static final String SOLD_OUT = "-1";

    private static final String NOT_PREPARED = "-2";

    private static final String DUPLICATED = "-3";

    /**
     * 抢红包脚本。先判库存是否存在，再去重，最后才弹出，全程在 Redis 单线程里一次完成。
     */
    private static final String RESERVE_SCRIPT = """
            if redis.call('exists', KEYS[1]) == 0 then
                return '-2'
            end
            if redis.call('sismember', KEYS[4], ARGV[1]) == 1 then
                return '-3'
            end
            local remainCount = tonumber(redis.call('get', KEYS[1]))
            if remainCount == nil then
                return '-2'
            end
            if remainCount <= 0 then
                return '-1'
            end
            local item = redis.call('rpop', KEYS[3])
            if not item then
                if remainCount > 0 then
                    return '-2'
                end
                return '-1'
            end
            local seq, amount = string.match(item, '^(%d+):(%d+)$')
            if seq == nil then
                return '-2'
            end
            redis.call('decr', KEYS[1])
            redis.call('decrby', KEYS[2], amount)
            redis.call('sadd', KEYS[4], ARGV[1])
            return item
            """;

    /**
     * 归还脚本。与抢的脚本严格互逆，用于落库失败后的补偿。
     */
    private static final String RESTORE_SCRIPT = """
            redis.call('lpush', KEYS[3], ARGV[1])
            redis.call('incr', KEYS[1])
            redis.call('incrby', KEYS[2], ARGV[2])
            redis.call('srem', KEYS[4], ARGV[3])
            return 1
            """;

    /**
     * 重建脚本。清空后整体重放，并把已领取用户写回去重集合。
     * 参数排布为 过期秒数、份额数、总金额、份额列表、已领取用户列表。
     */
    private static final String REBUILD_SCRIPT = """
            local ttl = tonumber(ARGV[1])
            local itemCount = tonumber(ARGV[2])
            local total = tonumber(ARGV[3])
            redis.call('del', KEYS[1], KEYS[2], KEYS[3], KEYS[4])
            if itemCount > 0 then
                local items = {}
                for i = 1, itemCount do
                    items[i] = ARGV[3 + i]
                end
                redis.call('rpush', KEYS[3], unpack(items))
            end
            for i = 4 + itemCount, #ARGV do
                redis.call('sadd', KEYS[4], ARGV[i])
            end
            redis.call('set', KEYS[1], itemCount)
            redis.call('set', KEYS[2], total)
            for i = 1, 4 do
                redis.call('expire', KEYS[i], ttl)
            end
            return itemCount
            """;

    private static final RedisScript<String> RESERVE = new DefaultRedisScript<>(RESERVE_SCRIPT, String.class);

    private static final RedisScript<Long> RESTORE = new DefaultRedisScript<>(RESTORE_SCRIPT, Long.class);

    private static final RedisScript<Long> REBUILD = new DefaultRedisScript<>(REBUILD_SCRIPT, Long.class);

    /**
     * redisService，业务服务层。
     */
    private final RedisService redisService;

    /**
     * 库存副本的过期时间。到期后队列消失，靠库里的份额重建。
     */
    @Value("${dong.redpacket.stock-ttl:24h}")
    private Duration stockTtl;

    /**
     * 预热库存，按份额序号整体入队。
     */
    @Override
    public void prepare(String packetNo, List<RedPacketItem> items, long totalAmount) {
        rebuild(packetNo, items, List.of());
        log.info("red packet stock prepared packetNo={} count={} total={} ttl={}",
                packetNo, items.size(), totalAmount, stockTtl);
    }

    /**
     * 抢一份红包，原子弹出并返回份额，库存异常时返回对应状态。
     */
    @Override
    public GrabReservation reserve(String packetNo, Long userId) {
        String result;
        try {
            result = redisService.execute(RESERVE, keysOf(packetNo), userId);
        } catch (DataAccessException ex) {
            log.warn("red packet stock unavailable packetNo={} reason={}", packetNo, ex.getMessage());
            return GrabReservation.of(GrabStatus.UNAVAILABLE);
        }
        if (result == null) {
            return GrabReservation.of(GrabStatus.UNAVAILABLE);
        }
        if (SOLD_OUT.equals(result)) {
            return GrabReservation.of(GrabStatus.SOLD_OUT);
        }
        if (NOT_PREPARED.equals(result)) {
            return GrabReservation.of(GrabStatus.NOT_PREPARED);
        }
        if (DUPLICATED.equals(result)) {
            return GrabReservation.of(GrabStatus.DUPLICATED);
        }
        int separator = result.indexOf(':');
        if (separator <= 0) {
            log.warn("malformed red packet item in queue packetNo={} value={}", packetNo, result);
            return GrabReservation.of(GrabStatus.NOT_PREPARED);
        }
        int seq = Integer.parseInt(result.substring(0, separator));
        long amount = Long.parseLong(result.substring(separator + 1));
        return GrabReservation.grabbed(amount, seq, false);
    }

    /**
     * 归还一份库存。归还失败只告警不抛异常，剩下的差额由对账任务兜底。
     */
    @Override
    public void restore(String packetNo, Long userId, long amount, int seq) {
        try {
            redisService.execute(RESTORE, keysOf(packetNo), seq + ":" + amount, amount, userId);
        } catch (DataAccessException ex) {
            log.warn("failed to restore red packet stock packetNo={} seq={} reason={}",
                    packetNo, seq, ex.getMessage());
        }
    }

    /**
     * 查询剩余份数，库存未预热返回 -1。
     */
    @Override
    public int remainCount(String packetNo) {
        String value = redisService.get(COUNT + packetNo).orElse(null);
        return value == null ? -1 : Integer.parseInt(value);
    }

    /**
     * 查询剩余金额，库存未预热返回 -1。
     */
    @Override
    public long remainAmount(String packetNo) {
        String value = redisService.get(AMOUNT + packetNo).orElse(null);
        return value == null ? -1L : Long.parseLong(value);
    }

    /**
     * 库存是否已预热。
     */
    @Override
    public boolean prepared(String packetNo) {
        return redisService.hasKey(COUNT + packetNo);
    }

    /**
     * 用库里的未领取份额整体重建队列。
     */
    @Override
    public void rebuild(String packetNo, List<RedPacketItem> pendingItems, List<Long> claimedUserIds) {
        long total = pendingItems.stream().mapToLong(RedPacketItem::getAmount).sum();
        List<Object> args = new ArrayList<>(3 + pendingItems.size() + claimedUserIds.size());
        args.add(stockTtl.toSeconds());
        args.add(pendingItems.size());
        args.add(total);
        pendingItems.forEach(item -> args.add(item.toQueueValue()));
        claimedUserIds.forEach(args::add);
        redisService.execute(REBUILD, keysOf(packetNo), args.toArray());
    }

    /**
     * 四个库存键的顺序：计数、金额、待发队列、已领取用户。
     */
    private List<String> keysOf(String packetNo) {
        return List.of(COUNT + packetNo, AMOUNT + packetNo, LIST + packetNo, USERS + packetNo);
    }

}
