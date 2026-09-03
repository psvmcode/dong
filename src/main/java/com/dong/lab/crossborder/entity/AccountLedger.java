package com.dong.lab.crossborder.entity;

import com.dong.lab.crossborder.enums.LedgerDirection;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账务流水。remittanceNo、accountId、direction 三者组成唯一索引，
 * 保证同一笔汇款对同一账户同一方向只记一次账，
 * 这是消息重复投递时不会重复记账的关键。
 */
@Data
public class AccountLedger {

    private Long id;

    /** 流水号，全局唯一，便于单条追溯 */
    private String ledgerNo;

    /** 关联的汇款单号 */
    private String remittanceNo;

    /** 记账账户 */
    private Long accountId;

    /** 方向：借方扣款、贷方入账 */
    private LedgerDirection direction;

    private String currency;

    /** 发生金额，恒为正数，方向由 direction 表达 */
    private BigDecimal amount;

    /** 记账后的余额快照，流水序列可完整还原余额变化 */
    private BigDecimal balanceAfter;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
