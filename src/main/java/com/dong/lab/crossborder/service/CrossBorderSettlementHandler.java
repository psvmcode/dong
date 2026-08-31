package com.dong.lab.crossborder.service;

import com.dong.lab.common.util.JsonUtils;
import com.dong.lab.crossborder.entity.CrossBorderRemittance;
import com.dong.lab.crossborder.enums.RemittanceStatus;
import com.dong.lab.crossborder.mapper.CrossBorderRemittanceMapper;
import com.dong.lab.crossborder.service.CrossBorderLedgerService;
import com.dong.lab.framework.mq.MessageHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * 清算消息消费者。收到扣款成功的消息后给收款方入账。
 *
 * <p>幂等由两层保证：先判断当前状态，已结算直接跳过；
 * 入账走 CrossBorderLedgerService 的事务方法，
 * 流水唯一索引冲突会让整个事务回滚，加钱操作一并撤销。
 *
 * <p>这里的入账必须走独立 bean 而不是本类内部方法：
 * 同类 this 调用绕过 Spring 代理，事务静默失效，
 * 曾经因此出现并发消息重复入账六次的事故。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrossBorderSettlementHandler implements MessageHandler {

    private final CrossBorderRemittanceMapper remittanceMapper;

    private final CrossBorderLedgerService ledgerService;

    private final LongAdder processed = new LongAdder();

    private final LongAdder duplicated = new LongAdder();

    private final LongAdder skipped = new LongAdder();

    @Override
    public String topic() {
        return "cross-border-settlement";
    }

    @Override
    public boolean handle(String key, String payload) {
        Map<String, Object> message = JsonUtils.fromJson(payload, new TypeReference<>() {
        });
        String remittanceNo = String.valueOf(message.get("remittanceNo"));
        CrossBorderRemittance remittance = remittanceMapper.selectByRemittanceNo(remittanceNo);
        if (remittance == null) {
            skipped.increment();
            log.warn("settlement message references unknown remittanceNo={}", remittanceNo);
            return true;
        }
        if (remittance.getStatus() == RemittanceStatus.SETTLED) {
            duplicated.increment();
            log.info("duplicate settlement message ignored remittanceNo={}", remittanceNo);
            return true;
        }
        if (remittance.getStatus() != RemittanceStatus.FUNDS_DEBITED) {
            skipped.increment();
            log.warn("skip settlement remittanceNo={} currentStatus={}", remittanceNo, remittance.getStatus());
            return true;
        }
        creditAndAdvance(remittance);
        processed.increment();
        return true;
    }

    private void creditAndAdvance(CrossBorderRemittance remittance) {
        ledgerService.creditAndAdvance(remittance);
    }

    public long processedCount() {
        return processed.sum();
    }

    public long duplicatedCount() {
        return duplicated.sum();
    }

    public long skippedCount() {
        return skipped.sum();
    }

}
