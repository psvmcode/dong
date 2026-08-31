package com.dong.lab.crossborder.service;

import com.dong.lab.common.util.JsonUtils;
import com.dong.lab.crossborder.entity.CrossBorderRemittance;
import com.dong.lab.crossborder.enums.RemittanceStatus;
import com.dong.lab.crossborder.mapper.CrossBorderRemittanceMapper;
import com.dong.lab.framework.mq.MqFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * 跨境支付维护任务。三个兜底动作缺一不可：
 * 汇率过期保证过期报价不再可被锁定；
 * 批次关闭驱动清算窗口推进；
 * 消息补偿是最终一致的关键——扣款成功但消息发送失败的汇款单必须重试推进，
 * 否则资金已扣却永远到不了账。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrossBorderMaintenanceTask {

    private final FxQuoteService fxQuoteService;

    private final SettlementService settlementService;

    private final CrossBorderRemittanceMapper remittanceMapper;

    private final MqFacade mqFacade;

    private final LongAdder compensationRounds = new LongAdder();

    private final LongAdder compensatedMessages = new LongAdder();

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
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 45_000)
    public void compensateSettlementMessages() {
        List<CrossBorderRemittance> pending = remittanceMapper.selectByStatus(RemittanceStatus.FUNDS_DEBITED, 50);
        if (pending.isEmpty()) {
            return;
        }
        compensationRounds.increment();
        for (CrossBorderRemittance remittance : pending) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("remittanceNo", remittance.getRemittanceNo());
            payload.put("batchNo", remittance.getBatchNo());
            payload.put("targetAmount", remittance.getTargetAmount().toPlainString());
            payload.put("payeeAccountId", remittance.getPayeeAccountId());
            payload.put("currency", remittance.getTargetCurrency());
            payload.put("channel", remittance.getChannel().getCode());
            payload.put("occurredAt", LocalDateTime.now().toString());
            try {
                mqFacade.sendOrdered("cross-border-settlement", remittance.getRemittanceNo(),
                        JsonUtils.toJson(payload), String.valueOf(remittance.getPayeeAccountId()));
                compensatedMessages.increment();
            } catch (Exception ex) {
                log.error("compensation send failed remittanceNo={}", remittance.getRemittanceNo(), ex);
            }
        }
    }

    public long compensationRoundsCount() {
        return compensationRounds.sum();
    }

    public long compensatedMessagesCount() {
        return compensatedMessages.sum();
    }

}
