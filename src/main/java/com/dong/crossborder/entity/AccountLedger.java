package com.dong.crossborder.entity;

import com.dong.crossborder.enums.LedgerDirection;
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

    /**
     * 主键
     */
    private Long id;

    /**
     * 流水号，全局唯一
     */
    private String ledgerNo;

    /**
     * 关联的汇款单号，同一笔汇款的多条流水共用
     */
    private String remittanceNo;

    /**
     * 账户 id
     */
    private Long accountId;

    /**
     * 记账方向，1 借方支出 2 贷方收入
     */
    private LedgerDirection direction;

    /**
     * 币种
     */
    private String currency;

    /**
     * 发生金额
     */
    private BigDecimal amount;

    /**
     * 记账后的余额快照，用于还原任意时点的账务状态
     */
    private BigDecimal balanceAfter;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
