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

    /** 汇款单号，对外业务标识 */
    private String remittanceNo;

    /** 幂等键，调用方生成，唯一索引防重复汇款 */
    private String idempotentKey;

    /** 付款账户 */
    private Long payerAccountId;

    /** 收款账户 */
    private Long payeeAccountId;

    /** 源币种，付款账户币种 */
    private String sourceCurrency;

    /** 目标币种，收款账户币种 */
    private String targetCurrency;

    /** 汇出金额，手续费另算 */
    private BigDecimal sourceAmount;

    /** 锁定汇率，挂起期间为 0，锁汇后回填 */
    private BigDecimal exchangeRate;

    /** 收款方到账金额 = 源金额 × 汇率 */
    private BigDecimal targetAmount;

    /** 渠道手续费 */
    private BigDecimal feeAmount;

    /** 清算渠道 */
    private SettlementChannel channel;

    /** 汇款单状态，单向推进 */
    private RemittanceStatus status;

    /** 合规结论，与 ComplianceRecord 对应 */
    private Integer complianceStatus;

    /** 关联的汇率报价号 */
    private String quoteNo;

    /** 所属清算批次，未进批为空 */
    private String batchNo;

    /** 失败或挂起原因 */
    private String failReason;

    /** 乐观锁版本号，并发推进状态的仲裁依据 */
    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
