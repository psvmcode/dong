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

    /**
     * 主键
     */
    private Long id;

    /**
     * 汇款单号，全局唯一
     */
    private String remittanceNo;

    /**
     * 幂等键，调用方生成，唯一索引防止重复汇款
     */
    private String idempotentKey;

    /**
     * 付款方账户 id
     */
    private Long payerAccountId;

    /**
     * 收款方账户 id
     */
    private Long payeeAccountId;

    /**
     * 源币种
     */
    private String sourceCurrency;

    /**
     * 目标币种
     */
    private String targetCurrency;

    /**
     * 源币种金额
     */
    private BigDecimal sourceAmount;

    /**
     * 成交汇率，锁汇后写入
     */
    private BigDecimal exchangeRate;

    /**
     * 目标币种金额，源金额乘以锁定汇率换算
     */
    private BigDecimal targetAmount;

    /**
     * 手续费，按渠道费率计算
     */
    private BigDecimal feeAmount;

    /**
     * 清算渠道，1 SWIFT 2 CIPS 3 本地清算
     */
    private SettlementChannel channel;

    /**
     * 汇款状态，状态机单向推进
     */
    private RemittanceStatus status;

    /**
     * 合规结论编码，0 未检 1 通过 2 拒绝 3 转人工
     */
    private Integer complianceStatus;

    /**
     * 引用的汇率报价单号
     */
    private String quoteNo;

    /**
     * 所属清算批次号，实时清算由消费者归批
     */
    private String batchNo;

    /**
     * 失败或挂起原因
     */
    private String failReason;

    /**
     * 乐观锁版本号，状态推进时递增
     */
    private Integer version;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
