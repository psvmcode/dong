package com.dong.lab.crossborder.entity;

import com.dong.lab.crossborder.enums.RemittanceStatus;
import com.dong.lab.crossborder.enums.SettlementChannel;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 汇款单。idempotentKey 上的唯一索引是防重复汇款的最后一道防线：
 * 网络超时后客户端重试会携带同一个幂等键，数据库直接拒绝第二次插入。
 * version 用于乐观锁，避免并发推进状态时互相覆盖。
 */
@Data
public class CrossBorderRemittance {

    private Long id;

    private String remittanceNo;

    private String idempotentKey;

    private Long payerAccountId;

    private Long payeeAccountId;

    private String sourceCurrency;

    private String targetCurrency;

    private BigDecimal sourceAmount;

    private BigDecimal exchangeRate;

    private BigDecimal targetAmount;

    private BigDecimal feeAmount;

    private SettlementChannel channel;

    private RemittanceStatus status;

    private Integer complianceStatus;

    private String quoteNo;

    private String batchNo;

    private String failReason;

    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
