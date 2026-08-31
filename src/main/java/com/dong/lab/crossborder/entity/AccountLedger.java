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

    private String ledgerNo;

    private String remittanceNo;

    private Long accountId;

    private LedgerDirection direction;

    private String currency;

    private BigDecimal amount;

    private BigDecimal balanceAfter;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
