package com.dong.lab.crossborder.service;

import com.dong.lab.crossborder.entity.CrossBorderAccount;
import com.dong.lab.crossborder.entity.CrossBorderRemittance;

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
     * 收款方入账：加余额、记贷方流水、推进状态，三者同事务。
     * 流水唯一索引保证重复消息只会让事务回滚，不会重复加钱。
     */
    void creditAndAdvance(CrossBorderRemittance remittance);

}
