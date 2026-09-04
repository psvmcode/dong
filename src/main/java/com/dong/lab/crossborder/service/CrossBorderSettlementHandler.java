package com.dong.lab.crossborder.service;

import com.dong.lab.common.util.JsonUtils;
import com.dong.lab.crossborder.entity.CrossBorderRemittance;
import com.dong.lab.crossborder.entity.SettlementBatch;
import com.dong.lab.crossborder.enums.RemittanceStatus;
import com.dong.lab.crossborder.enums.SettlementChannel;
import com.dong.lab.crossborder.enums.SettlementStatus;
import com.dong.lab.crossborder.mapper.CrossBorderRemittanceMapper;
import com.dong.lab.crossborder.mapper.SettlementBatchMapper;
import com.dong.lab.crossborder.service.CrossBorderLedgerService;
import com.dong.lab.framework.mq.MessageHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
 *
 * <p>入账时会把汇款单归入当前打开的清算批次，没有就自动创建一个。
 * 不归批的代价是对账按批次拉取时查不到这些单子，
 * 整条实时清算链路的资金就成了对账盲区。
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class CrossBorderSettlementHandler implements MessageHandler {

    private static final long DEFAULT_CUTOFF_MINUTES = 30L;

    /**
     * remittanceMapper，MyBatis Mapper 数据访问层。
     */
    private final CrossBorderRemittanceMapper remittanceMapper;

    /**
     * batchMapper，MyBatis Mapper 数据访问层。
     */
    private final SettlementBatchMapper batchMapper;

    /**
     * ledgerService，业务服务层。
     */
    private final CrossBorderLedgerService ledgerService;

    private final LongAdder processed = new LongAdder();

    private final LongAdder duplicated = new LongAdder();

    private final LongAdder skipped = new LongAdder();

    /**
     * topic。
     */
    @Override
    public String topic() {
        return "cross-border-settlement";
    }

    /**
     * handle。
     */
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
        assignBatch(remittance);
        creditAndAdvance(remittance);
        processed.increment();
        return true;
    }

    /**
     * 归入当前打开的批次。找不到打开的批次就建一个，
     * 批次计数同步累加。归批失败不阻断入账，只是该单子的批次字段留空，
     * 对账时作为未归批单据单独处理，资金动作不能因为对账组织问题而延迟。
     */
    private void assignBatch(CrossBorderRemittance remittance) {
        try {
            SettlementChannel channel = remittance.getChannel() == null
                    ? SettlementChannel.SWIFT : remittance.getChannel();
            SettlementBatch batch = batchMapper.selectOpenByChannelAndCurrency(channel, remittance.getTargetCurrency());
            if (batch == null) {
                batch = new SettlementBatch();
                batch.setBatchNo("SB" + System.nanoTime());
                batch.setChannel(channel);
                batch.setCurrency(remittance.getTargetCurrency());
                batch.setTotalCount(0);
                batch.setTotalAmount(BigDecimal.ZERO);
                batch.setStatus(SettlementStatus.OPEN);
                batch.setCutoffTime(LocalDateTime.now().plusMinutes(DEFAULT_CUTOFF_MINUTES));
                batchMapper.insert(batch);
                log.info("settlement batch auto created batchNo={} channel={} currency={}",
                        batch.getBatchNo(), channel, remittance.getTargetCurrency());
            }
            remittanceMapper.updateBatchNo(remittance.getRemittanceNo(), batch.getBatchNo());
            batchMapper.updateTotal(batch.getBatchNo(), batch.getTotalCount() + 1,
                    batch.getTotalAmount().add(remittance.getTargetAmount()));
            remittance.setBatchNo(batch.getBatchNo());
        } catch (Exception ex) {
            log.warn("assign batch failed remittanceNo={}", remittance.getRemittanceNo(), ex);
        }
    }

    /**
     * creditAndAdvance。
     */
    private void creditAndAdvance(CrossBorderRemittance remittance) {
        ledgerService.creditAndAdvance(remittance);
    }

    /**
     * processedCount。
     */
    public long processedCount() {
        return processed.sum();
    }

    /**
     * duplicatedCount。
     */
    public long duplicatedCount() {
        return duplicated.sum();
    }

    /**
     * skippedCount。
     */
    public long skippedCount() {
        return skipped.sum();
    }

}
