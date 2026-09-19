package com.dong.redpacket.task;

import com.dong.redpacket.entity.RedPacket;
import com.dong.redpacket.enums.RedPacketStatus;
import com.dong.redpacket.mapper.RedPacketItemMapper;
import com.dong.redpacket.mapper.RedPacketMapper;
import com.dong.redpacket.service.RedPacketService;
import com.dong.redpacket.service.RedPacketStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
/**
 * 红包库存对账。定时把"库里的未领取份额"和"Redis 里的剩余"对齐，
 * 覆盖三种自救场景：Redis 整体不可用期间走数据库降级留下的偏差、
 * 库存副本过期后没人来抢、以及落库失败后归还也没成功留下的差额。
 *
 * <p>判断基准一律是数据库份额表，Redis 只是副本，
 * 副本与基准不一致就重建副本，绝不反过来改库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dong.redpacket", name = "consistency-task-enabled",
        havingValue = "true", matchIfMissing = true)
public class RedPacketConsistencyTask {

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
     * redPacketService，业务服务层。
     */
    private final RedPacketService redPacketService;

    /**
     * 单次扫描的红包数量，防止红包很多时一次捞全量。
     */
    @Value("${dong.redpacket.consistency-scan-limit:50}")
    private int scanLimit;

    /**
     * 对账未结束的红包，无差异时保持安静。
     */
    @Scheduled(fixedDelayString = "${dong.redpacket.consistency-interval-ms:300000}", initialDelay = 60_000)
    public void reconcile() {
        List<RedPacket> packets = redPacketMapper.selectUnfinished(scanLimit);
        packets.forEach(this::check);
    }

    /**
     * 核对单个红包。
     */
    private void check(RedPacket packet) {
        String packetNo = packet.getPacketNo();
        int pendingCount = redPacketItemMapper.countUnclaimed(packetNo);
        long pendingAmount = redPacketItemMapper.sumUnclaimed(packetNo);
        if (pendingCount == 0) {
            if (packet.getStatus() != RedPacketStatus.FINISHED) {
                redPacketMapper.updateStatus(packetNo, RedPacketStatus.FINISHED.getCode());
                log.info("red packet marked finished by reconciliation packetNo={}", packetNo);
            }
            return;
        }
        if (packet.getRemainCount() != pendingCount || packet.getRemainAmount() != pendingAmount) {
            redPacketMapper.updateRemain(packetNo, pendingAmount, pendingCount);
            log.warn("red packet remain corrected to unclaimed shares packetNo={} count={} amount={}",
                    packetNo, pendingCount, pendingAmount);
        }
        RemainSnapshot snapshot = readRemain(packetNo);
        if (snapshot == null) {
            return;
        }
        if (snapshot.count() == pendingCount && snapshot.amount() == pendingAmount) {
            return;
        }
        redPacketService.rebuild(packetNo);
        log.info("red packet stock inconsistent and rebuilt packetNo={} db={}/{} redis={}/{}",
                packetNo, pendingCount, pendingAmount, snapshot.count(), snapshot.amount());
    }

    /**
     * 读 Redis 侧的剩余，未预热返回 null 表示需要重建，Redis 不可用同样返回 null 但只告警。
     */
    private RemainSnapshot readRemain(String packetNo) {
        try {
            if (!redPacketStockService.prepared(packetNo)) {
                return null;
            }
            return new RemainSnapshot(redPacketStockService.remainCount(packetNo),
                    redPacketStockService.remainAmount(packetNo));
        } catch (Exception ex) {
            log.warn("skip red packet reconciliation, redis unavailable packetNo={} reason={}",
                    packetNo, ex.getMessage());
            return null;
        }
    }

    /**
     * Redis 侧剩余快照。
     */
    private record RemainSnapshot(int count, long amount) {

    }

}
