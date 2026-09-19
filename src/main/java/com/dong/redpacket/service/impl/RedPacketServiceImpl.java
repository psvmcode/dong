package com.dong.redpacket.service.impl;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.common.util.Snowflake;
import com.dong.framework.lock.DistributedLockService;
import com.dong.redpacket.dto.GrabReservation;
import com.dong.redpacket.dto.GrabResultResponse;
import com.dong.redpacket.dto.RedPacketSendRequest;
import com.dong.redpacket.entity.RedPacket;
import com.dong.redpacket.entity.RedPacketItem;
import com.dong.redpacket.entity.RedPacketRecord;
import com.dong.redpacket.enums.GrabStatus;
import com.dong.redpacket.enums.RedPacketStatus;
import com.dong.redpacket.enums.RedPacketType;
import com.dong.redpacket.mapper.RedPacketItemMapper;
import com.dong.redpacket.mapper.RedPacketMapper;
import com.dong.redpacket.support.GrabRateLimiter;
import com.dong.redpacket.support.RedPacketMetrics;
import com.dong.redpacket.support.RedPacketSoldOutFlag;
import com.dong.redpacket.support.RedPacketAllocator;
import com.dong.redpacket.service.RedPacketService;
import com.dong.redpacket.service.RedPacketStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
/**
 * 抢红包实现。五道防线依次生效：
 * 单用户限流挡住脚本刷，
 * 本地抢完标记挡住抢完之后的无效流量，
 * Redis 脚本保证去重与弹出原子完成，
 * 数据库份额状态兜底防同一份被两个人拿到，
 * 剩余金额与份数的扣减带充足条件，谁也不会扣成负数。
 *
 * <p>落库与库存是两阶段：先在 Redis 预扣，再落库，
 * 落库失败必须把预扣的份额还回去，否则这一份就凭空消失，谁也抢不到。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedPacketServiceImpl implements RedPacketService {

    private static final String PACKET_NO_PREFIX = "RP";

    private static final String REBUILD_LOCK = "lab:redpacket:rebuild:";

    /**
     * 单个红包的份数上限。不封顶的话一次发几十万份会把批量插入和 Redis 队列一起拖垮。
     */
    private static final int MAX_TOTAL_COUNT = 1000;

    /**
     * 数据库兜底时抢占份额的重试次数，用完仍失败就让调用方重试，不在原地死循环。
     */
    private static final int DB_CLAIM_ATTEMPTS = 3;

    /**
     * redPacketMapper，MyBatis Mapper 数据访问层。
     */
    private final RedPacketMapper redPacketMapper;

    /**
     * redPacketItemMapper，MyBatis Mapper 数据访问层。
     */
    private final RedPacketItemMapper redPacketItemMapper;

    /**
     * redPacketStockService，业务服务层。
     */
    private final RedPacketStockService redPacketStockService;

    /**
     * 分布式锁，重建库存时用它避免并发重复重建。
     */
    private final DistributedLockService distributedLockService;

    /**
     * 单用户抢红包限流器。
     */
    private final GrabRateLimiter grabRateLimiter;

    /**
     * 本地抢完标记。
     */
    private final RedPacketSoldOutFlag soldOutFlag;

    /**
     * 运行时计数器。
     */
    private final RedPacketMetrics metrics;

    /**
     * snowflake，分布式唯一 ID 生成器。
     */
    private final Snowflake snowflake;

    /**
     * Redis 不可用时是否允许降级到数据库，关掉后 Redis 故障直接返回 1005。
     */
    @Value("${dong.redpacket.db-fallback-enabled:true}")
    private boolean dbFallbackEnabled;

    /**
     * 发红包，预分配金额落库后预热 Redis 并返回红包编号。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String send(RedPacketSendRequest request) {
        if (request.getTotalCount() > MAX_TOTAL_COUNT) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID,
                    "total count must not exceed " + MAX_TOTAL_COUNT);
        }
        String packetNo = PACKET_NO_PREFIX + snowflake.nextId();
        RedPacket redPacket = request.toEntity(packetNo);
        redPacketMapper.insert(redPacket);
        List<Long> amounts = redPacket.getPacketType() == RedPacketType.FIXED
                ? RedPacketAllocator.allocateFixed(request.getTotalAmount(), request.getTotalCount())
                : RedPacketAllocator.allocate(request.getTotalAmount(), request.getTotalCount());
        List<RedPacketItem> items = new ArrayList<>(amounts.size());
        for (int seq = 0; seq < amounts.size(); seq++) {
            items.add(RedPacketItem.pending(packetNo, seq, amounts.get(seq)));
        }
        insertItems(items);
        try {
            redPacketStockService.prepare(packetNo, items, request.getTotalAmount());
        } catch (Exception ex) {
            log.warn("red packet stock prepare failed, it will be rebuilt on first grab packetNo={} reason={}",
                    packetNo, ex.getMessage());
        }
        log.info("red packet sent packetNo={} total={} count={}", packetNo,
                request.getTotalAmount(), request.getTotalCount());
        return packetNo;
    }

    /**
     * 抢红包，先限流再去库存，落库失败则归还预扣的份额。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public GrabResultResponse grab(String packetNo, Long userId) {
        grabRateLimiter.check(userId);
        RedPacket redPacket = findByPacketNo(packetNo);
        if (redPacket.getStatus() == RedPacketStatus.FINISHED) {
            return GrabResultResponse.failed("red packet is finished");
        }
        if (redPacket.getStatus() == RedPacketStatus.EXPIRED) {
            return GrabResultResponse.failed("red packet is expired");
        }
        if (soldOutFlag.isSoldOut(packetNo)) {
            return GrabResultResponse.failed("red packet is finished");
        }

        GrabReservation reservation = reserve(packetNo, userId);
        if (!reservation.grabbed()) {
            return GrabResultResponse.failed(messageOf(reservation.status()));
        }
        if (!claimShare(packetNo, userId, reservation)) {
            metrics.staleShare();
            log.warn("stale share discarded packetNo={} seq={}", packetNo, reservation.seq());
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "share was stale, please retry");
        }
        try {
            writeGrab(packetNo, userId, reservation);
        } catch (DuplicateKeyException ex) {
            rollbackReservation(packetNo, userId, reservation);
            throw new BusinessException(Constants.CODE_IDEMPOTENT_REJECTED, "already grabbed");
        } catch (Exception ex) {
            rollbackReservation(packetNo, userId, reservation);
            throw ex;
        }

        RedPacket latest = findByPacketNo(packetNo);
        if (latest.getStatus() == RedPacketStatus.CREATED) {
            redPacketMapper.updateStatus(packetNo, RedPacketStatus.DISTRIBUTING.getCode());
        }
        if (latest.getRemainCount() <= 0) {
            redPacketMapper.updateStatus(packetNo, RedPacketStatus.FINISHED.getCode());
            soldOutFlag.mark(packetNo);
        }
        log.info("red packet grabbed packetNo={} user={} amount={} seq={} degraded={}",
                packetNo, userId, reservation.amount(), reservation.seq(), reservation.itemClaimed());
        return GrabResultResponse.success(reservation.amount());
    }

    /**
     * 按红包编号查询红包详情。
     */
    @Override
    public RedPacket findByPacketNo(String packetNo) {
        RedPacket redPacket = redPacketMapper.selectByPacketNo(packetNo);
        if (redPacket == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "red packet " + packetNo + " not found");
        }
        return redPacket;
    }

    /**
     * 查询红包领取记录。
     */
    @Override
    public List<RedPacketRecord> records(String packetNo) {
        return redPacketMapper.selectRecords(packetNo);
    }

    /**
     * 查询剩余份数，Redis 不可用或未预热时回落到数据库。
     */
    @Override
    public int remainCount(String packetNo) {
        try {
            if (redPacketStockService.prepared(packetNo)) {
                int remain = redPacketStockService.remainCount(packetNo);
                if (remain >= 0) {
                    return remain;
                }
            }
        } catch (Exception ex) {
            log.warn("read red packet remain count from redis failed packetNo={} reason={}",
                    packetNo, ex.getMessage());
        }
        return findByPacketNo(packetNo).getRemainCount();
    }

    /**
     * 查询剩余金额，Redis 不可用或未预热时回落到数据库。
     */
    @Override
    public long remainAmount(String packetNo) {
        try {
            if (redPacketStockService.prepared(packetNo)) {
                long remain = redPacketStockService.remainAmount(packetNo);
                if (remain >= 0) {
                    return remain;
                }
            }
        } catch (Exception ex) {
            log.warn("read red packet remain amount from redis failed packetNo={} reason={}",
                    packetNo, ex.getMessage());
        }
        return findByPacketNo(packetNo).getRemainAmount();
    }

    /**
     * 从数据库重建 Redis 库存，加锁避免并发重复重建。
     */
    @Override
    public boolean rebuild(String packetNo) {
        try {
            Boolean rebuilt = distributedLockService.execute(REBUILD_LOCK + packetNo,
                    Duration.ofSeconds(10), Duration.ofSeconds(3), () -> {
                        List<RedPacketItem> pending = redPacketItemMapper.selectUnclaimed(packetNo);
                        List<Long> claimedUsers = redPacketMapper.selectClaimedUserIds(packetNo);
                        redPacketStockService.rebuild(packetNo, pending, claimedUsers);
                        soldOutFlag.clear(packetNo);
                        metrics.stockRebuilt();
                        log.info("red packet stock rebuilt packetNo={} pending={} claimed={}",
                                packetNo, pending.size(), claimedUsers.size());
                        return Boolean.TRUE;
                    });
            return Boolean.TRUE.equals(rebuilt);
        } catch (Exception ex) {
            log.warn("red packet stock rebuild skipped packetNo={} reason={}", packetNo, ex.getMessage());
            return false;
        }
    }

    /**
     * 查看运行时状态。
     */
    @Override
    public Map<String, Object> runtime() {
        Map<String, Object> runtime = new LinkedHashMap<>(metrics.snapshot());
        runtime.put("soldOutShortCircuited", soldOutFlag.shortCircuitedCount());
        return runtime;
    }

    /**
     * 取库存。Redis 正常时一次弹出即可，
     * 副本缺失先重建，Redis 整体不可用再降级到数据库。
     */
    private GrabReservation reserve(String packetNo, Long userId) {
        GrabReservation reservation = redPacketStockService.reserve(packetNo, userId);
        GrabStatus status = reservation.status();
        if (status == GrabStatus.GRABBED) {
            return reservation;
        }
        if (status == GrabStatus.SOLD_OUT) {
            soldOutFlag.mark(packetNo);
            return reservation;
        }
        if (status == GrabStatus.DUPLICATED) {
            return reservation;
        }
        if (status == GrabStatus.NOT_PREPARED && rebuild(packetNo)) {
            GrabReservation retried = redPacketStockService.reserve(packetNo, userId);
            if (retried.grabbed()) {
                return retried;
            }
            if (retried.status() == GrabStatus.SOLD_OUT) {
                soldOutFlag.mark(packetNo);
            }
            if (retried.status() != GrabStatus.NOT_PREPARED && retried.status() != GrabStatus.UNAVAILABLE) {
                return retried;
            }
        }
        metrics.redisUnavailable();
        if (!dbFallbackEnabled) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "red packet stock is unavailable");
        }
        return reserveByDb(packetNo, userId);
    }

    /**
     * 数据库兜底抢一份。随机序号起步把并发打散到不同份额上，
     * 占位靠 status = 0 这个条件，抢不到就换下一份，重试用完让调用方重试。
     */
    private GrabReservation reserveByDb(String packetNo, Long userId) {
        if (redPacketItemMapper.countClaimedByUser(packetNo, userId) > 0) {
            return GrabReservation.of(GrabStatus.DUPLICATED);
        }
        for (int attempt = 0; attempt < DB_CLAIM_ATTEMPTS; attempt++) {
            int seqFrom = ThreadLocalRandom.current().nextInt(MAX_TOTAL_COUNT);
            RedPacketItem candidate = redPacketItemMapper.selectClaimCandidate(packetNo, seqFrom);
            if (candidate == null) {
                candidate = redPacketItemMapper.selectClaimCandidate(packetNo, 0);
            }
            if (candidate == null) {
                return GrabReservation.of(GrabStatus.SOLD_OUT);
            }
            if (redPacketItemMapper.claim(packetNo, candidate.getSeq(), userId) > 0) {
                metrics.dbFallback();
                return GrabReservation.grabbed(candidate.getAmount(), candidate.getSeq(), true);
            }
        }
        throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "stock is busy, please retry");
    }

    /**
     * 占位份额。占不上说明队列里这一份在库里已经是别人的，
     * 属于副本脏数据，不归还而是直接丢弃，否则它会一直在队列里打转。
     */
    private boolean claimShare(String packetNo, Long userId, GrabReservation reservation) {
        if (reservation.itemClaimed()) {
            return true;
        }
        return redPacketItemMapper.claim(packetNo, reservation.seq(), userId) > 0;
    }

    /**
     * 写领取记录并扣减剩余。扣减带充足条件，
     * 影响行数为 0 说明库存真的不足，宁可失败也不能扣成负数。
     */
    private void writeGrab(String packetNo, Long userId, GrabReservation reservation) {
        RedPacketRecord record = new RedPacketRecord();
        record.setPacketNo(packetNo);
        record.setUserId(userId);
        record.setAmount(reservation.amount());
        redPacketMapper.insertRecord(record);
        int decreased = redPacketMapper.decreaseRemain(packetNo, reservation.amount(), 1);
        if (decreased == 0) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "red packet stock exhausted");
        }
    }

    /**
     * 归还预扣的份额。Redis 侧推回队列，数据库侧若已占位也要退回未领取。
     */
    private void rollbackReservation(String packetNo, Long userId, GrabReservation reservation) {
        if (reservation.itemClaimed()) {
            redPacketItemMapper.release(packetNo, reservation.seq(), userId);
        }
        redPacketStockService.restore(packetNo, userId, reservation.amount(), reservation.seq());
        metrics.stockRestored();
        log.warn("red packet reservation rolled back packetNo={} user={} seq={}",
                packetNo, userId, reservation.seq());
    }

    /**
     * 分批写入预分配份额，避免单次 SQL 参数过多。
     */
    private void insertItems(List<RedPacketItem> items) {
        for (int from = 0; from < items.size(); from += Constants.MAX_BATCH_SIZE) {
            int to = Math.min(from + Constants.MAX_BATCH_SIZE, items.size());
            redPacketItemMapper.batchInsert(items.subList(from, to));
        }
    }

    /**
     * 把库存判定结果翻译成给用户看的提示。
     */
    private String messageOf(GrabStatus status) {
        return switch (status) {
            case SOLD_OUT -> "red packet is finished";
            case DUPLICATED -> "already grabbed";
            case NOT_PREPARED, UNAVAILABLE -> "red packet stock is unavailable, please retry";
            default -> "grab failed";
        };
    }

}
