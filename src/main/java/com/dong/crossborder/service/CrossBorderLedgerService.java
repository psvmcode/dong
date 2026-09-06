package com.dong.crossborder.service;

import com.dong.crossborder.entity.CrossBorderAccount;
import com.dong.crossborder.entity.CrossBorderRemittance;

import java.math.BigDecimal;

/**
 * 账务操作。单独成 bean 是刻意的：
 * 事务注解只在经过 Spring 代理的调用上生效，
 * 同一个类内部 this 调用会绕过代理导致事务静默失效。
 * 曾经因此出现重复入账：并发消息各自提交了加钱，
 * 流水的唯一索引冲突却回滚不了已经提交的加钱操作。
 */
public interface CrossBorderLedgerService {

    /**
     * 扣款并落单：扣余额、插入汇款单、记借方流水，三者同事务。
     * 余额不足时整个事务回滚，不会出现扣了款却没单子的情况。
     */
    void debitAndPersist(CrossBorderRemittance remittance, CrossBorderAccount payer, BigDecimal totalDebit);

    /**
     * 对已落库的汇款单扣款：扣余额、记借方流水、推进状态，三者同事务。
     * 人工审核放行时使用——汇款单在挂起时已经插入，不能重复插入，
     * 只需扣款记账并从 QUOTE_LOCKED 推进到 FUNDS_DEBITED。
     */
    void debitExisting(CrossBorderRemittance remittance, CrossBorderAccount payer, BigDecimal totalDebit);

    /**
     * 标记为清算中：资金已交给清算渠道，等待渠道确认到账。
     *
     * <p>只有「已扣款」能推进到「清算中」；已处于清算中或已结算时直接返回 false，
     * 这两个都是「别人已经推进过了」，不是错误——手动清算与消息消费并发时必然出现，
     * 兜底清算遇到它应该跳过而不是报错。其余状态一律抛冲突，
     * 因为未扣款的单子根本不该出现在清算链路里。
     *
     * <p>返回 false 也是调用方判断「这次不是我推进的」的依据：
     * 归批这类不可重复的动作只在返回 true 时做。
     */
    boolean markSettling(CrossBorderRemittance remittance);

    /**
     * 渠道确认后入账：加余额、记贷方流水、推进到已结算，三者同事务。
     *
     * <p>要求汇款单当前处于「清算中」；已结算则返回 false 表示本次没有重复入账，
     * 调用方据此决定要不要计入「本次清算笔数」。
     * 流水唯一索引保证重复消息只会让事务回滚，不会重复加钱。
     */
    boolean creditAndAdvance(CrossBorderRemittance remittance);

}
