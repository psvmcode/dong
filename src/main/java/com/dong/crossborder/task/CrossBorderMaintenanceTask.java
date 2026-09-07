package com.dong.crossborder.task;

import com.dong.common.util.JsonUtils;
import com.dong.crossborder.dto.ReconReportResponse;
import com.dong.crossborder.dto.SettlementBatchResponse;
import com.dong.crossborder.entity.CrossBorderRemittance;
import com.dong.crossborder.enums.RemittanceStatus;
import com.dong.crossborder.enums.SettlementStatus;
import com.dong.crossborder.mapper.CrossBorderRemittanceMapper;
import com.dong.framework.mq.MqFacade;
import com.dong.crossborder.service.FxQuoteService;
import com.dong.crossborder.service.ReconciliationService;
import com.dong.crossborder.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
/**
 * 跨境支付维护任务。这五个兜底动作缺一不可：
 * 汇率过期保证过期报价不再可被锁定；
 * 批次关闭驱动清算窗口推进；
 * 消息补偿是最终一致的关键——扣款成功但消息发送失败的汇款单必须重试推进，
 * 否则资金已扣却永远到不了账，但重试必须有上限，坏单不能被无限重发；
 * 每日对账是发现资金差错的最后一道关，不能只提供手动接口；
 * 卡单检测让"钱扣了却没到账"这种最坏情况能被主动发现，而不是等客户来问。
 */
@Slf4j
@Component
@RequiredArgsConstructor

public class CrossBorderMaintenanceTask {

    /**
     * fxQuoteService，业务服务层。
     */
    private final FxQuoteService fxQuoteService;

    /**
     * settlementService，业务服务层。
     */
    private final SettlementService settlementService;

    /**
     * remittanceMapper，MyBatis Mapper 数据访问层。
     */
    private final CrossBorderRemittanceMapper remittanceMapper;

    /**
     * mqFacade。
     */
    private final MqFacade mqFacade;

    /**
     * reconciliationService。
     */
    private final ReconciliationService reconciliationService;

    /**
     * 静默期。创建后这段时间内不补偿，留给正常投递流程。
     */
    private static final Duration COMPENSATION_IDLE = Duration.ofMinutes(2);

    /**
     * 卡单阈值。已扣款与清算中在正常链路里只停留毫秒级，
     * 超过这个时间说明钱扣了却没到账，是最需要被主动发现的故障。
     */
    private static final Duration STUCK_THRESHOLD = Duration.ofMinutes(30);

    /**
     * 补偿重发次数上限。没有上限的补偿会把一笔坏单无限重发：
     * 每 30 秒一次，一天 2880 次，既占着扫描名额又不停往 MQ 塞消息。
     * 达到上限就停止自动重试并报错，交给人工处理。
     *
     * <p>停止自动重试不等于放弃：失败可能是可恢复的（收款账户冻结后又解冻），
     * 人工处理完调用 /remittance/{no}/retry 重置计数即可重新进入补偿。
     */
    private static final int MAX_COMPENSATION_RETRIES = 20;

    /**
     * 每日对账覆盖的批次时间窗，避免对历史批次做无意义的全量重跑。
     */
    private static final long RECON_WINDOW_DAYS = 7L;

    private final LongAdder compensationRounds = new LongAdder();

    private final LongAdder compensatedMessages = new LongAdder();

    private final LongAdder skippedInQuietPeriod = new LongAdder();

    private final LongAdder abandonedRemittance = new LongAdder();

    private final LongAdder stuckRounds = new LongAdder();

    private final LongAdder unmatchedBatches = new LongAdder();

    /**
     * 清理过期报价。定时执行而不是在锁汇时惰性判断，
     * 是为了让可用报价列表始终干净。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void expireQuotes() {
        int expired = fxQuoteService.expireOverdue();
        if (expired > 0) {
            log.info("expired {} fx quotes", expired);
        }
    }

    /**
     * 关闭到期批次。渠道清算窗口关闭后批次不再接收新汇款。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 90_000)
    public void closeBatches() {
        int closed = settlementService.closeOverdue();
        if (closed > 0) {
            log.info("closed {} settlement batches", closed);
        }
    }

    /**
     * 补偿扫描。重新发送已扣款但未收到清算确认的汇款单。
     * 消费端幂等，重复发送不会重复入账，所以这里重发是安全的。
     *
     * <p>静默期是必须的：刚创建的汇款单消息还在投递途中，
     * 若不加区分地立即重发，每轮都会把在途消息再发一遍，形成消息风暴。
     * 这里只补偿创建超过一定时间仍未推进的单子。
     *
     * <p>清算中也必须纳入扫描：消息已投递但入账没跑完的单子停在这个状态，
     * 只扫已扣款会把它漏掉，于是钱已出账却永远等不到确认。
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 45_000)
    public void compensateSettlementMessages() {
        List<CrossBorderRemittance> pending = new ArrayList<>();
        pending.addAll(remittanceMapper.selectByStatus(RemittanceStatus.FUNDS_DEBITED, 50));
        pending.addAll(remittanceMapper.selectByStatus(RemittanceStatus.SETTLING, 50));
        if (pending.isEmpty()) {
            return;
        }
        compensationRounds.increment();
        LocalDateTime threshold = LocalDateTime.now().minus(COMPENSATION_IDLE);
        for (CrossBorderRemittance remittance : pending) {
            if (remittance.getCreateTime() != null && remittance.getCreateTime().isAfter(threshold)) {
                skippedInQuietPeriod.increment();
                continue;
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("remittanceNo", remittance.getRemittanceNo());
            payload.put("batchNo", remittance.getBatchNo());
            payload.put("targetAmount", remittance.getTargetAmount().toPlainString());
            payload.put("payeeAccountId", remittance.getPayeeAccountId());
            payload.put("currency", remittance.getTargetCurrency());
            payload.put("channel", remittance.getChannel().getCode());
            payload.put("occurredAt", LocalDateTime.now().toString());
            int retried = remittance.getRetryCount() == null ? 0 : remittance.getRetryCount();
            if (retried >= MAX_COMPENSATION_RETRIES) {
                abandonedRemittance.increment();
                log.error("compensation abandoned after {} retries remittanceNo={} status={}, "
                                + "manual retry available at POST /api/crossborder/remittance/{}/retry",
                        retried, remittance.getRemittanceNo(), remittance.getStatus(),
                        remittance.getRemittanceNo());
                continue;
            }
            try {
                mqFacade.sendOrdered("cross-border-settlement", remittance.getRemittanceNo(),
                        JsonUtils.toJson(payload), String.valueOf(remittance.getPayeeAccountId()));
                remittanceMapper.increaseRetryCount(remittance.getRemittanceNo());
                compensatedMessages.increment();
            } catch (Exception ex) {
                log.error("compensation send failed remittanceNo={}", remittance.getRemittanceNo(), ex);
            }
        }
    }

    /**
     * 每日对账。对账是发现资金差错的最后一道关，只提供手动接口等于没有——
     * 人一定会忘。这里每天定时跑，跑出差异就报错，让人不得不看。
     *
     * <p>只处理近期关闭的批次：对账本身幂等（重跑会先清旧差异），
     * 但历史批次越积越多，每天全量重跑既无必要也会拖慢任务。
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void dailyReconcile() {
        LocalDateTime since = LocalDateTime.now().minusDays(RECON_WINDOW_DAYS);
        int reconciled = 0;
        for (SettlementBatchResponse batch : settlementService.findAll()) {
            if (batch.getStatus() == SettlementStatus.OPEN) {
                continue;
            }
            if (batch.getCutoffTime() == null || batch.getCutoffTime().isBefore(since)) {
                continue;
            }
            try {
                ReconReportResponse report = reconciliationService.reconcile(batch.getBatchNo(), 0.0);
                reconciled++;
                if (report.getDiffCount() > 0) {
                    unmatchedBatches.increment();
                    log.error("reconciliation diff found batchNo={} diffCount={} unhandled={}",
                            batch.getBatchNo(), report.getDiffCount(), report.getUnhandledCount());
                }
            } catch (Exception ex) {
                log.error("daily reconcile failed batchNo={}", batch.getBatchNo(), ex);
            }
        }
        if (reconciled > 0) {
            log.info("daily reconciliation finished, {} batch(es) reconciled", reconciled);
        }
    }

    /**
     * 卡单检测。资金已扣减却迟迟没有终结的单子，靠客户来问发现是来不及的。
     * 只覆盖已扣款与清算中：待审核要等人工，不作为卡单处理。
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    public void detectStuckRemittance() {
        LocalDateTime threshold = LocalDateTime.now().minus(STUCK_THRESHOLD);
        long debited = remittanceMapper.countStuckBefore(RemittanceStatus.FUNDS_DEBITED, threshold);
        long settling = remittanceMapper.countStuckBefore(RemittanceStatus.SETTLING, threshold);
        if (debited + settling == 0) {
            return;
        }
        stuckRounds.increment();
        log.error("stuck remittance detected debited={} settling={} olderThan={}", debited, settling, threshold);
    }

    /**
     * compensationRoundsCount。
     */
    public long compensationRoundsCount() {
        return compensationRounds.sum();
    }

    /**
     * compensatedMessagesCount。
     */
    public long compensatedMessagesCount() {
        return compensatedMessages.sum();
    }

    /**
     * skippedInQuietPeriodCount。
     */
    public long skippedInQuietPeriodCount() {
        return skippedInQuietPeriod.sum();
    }

    /**
     * stuckRoundsCount。
     */
    public long stuckRoundsCount() {
        return stuckRounds.sum();
    }

    /**
     * abandonedRemittanceCount。
     */
    public long abandonedRemittanceCount() {
        return abandonedRemittance.sum();
    }

    /**
     * unmatchedBatchesCount。
     */
    public long unmatchedBatchesCount() {
        return unmatchedBatches.sum();
    }

}
