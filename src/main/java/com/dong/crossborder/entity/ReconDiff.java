package com.dong.crossborder.entity;

import com.dong.crossborder.enums.ReconDiffType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 对账差异。每日对账把渠道回单与本地流水逐笔比对，
 * 比不上的都记到这里，由运营按差异类型处理。
 */
@Data

public class ReconDiff {

    /**
     * 主键
     */
    private Long id;

    /**
     * 清算批次号
     */
    private String batchNo;

    /**
     * 汇款单号，渠道多单时可能没有对应本地单
     */
    private String remittanceNo;

    /**
     * 差异类型，1 长款 2 短款 3 金额不一致 4 渠道有本地无 5 本地有渠道无
     */
    private ReconDiffType diffType;

    /**
     * 本地记录的金额
     */
    private BigDecimal localAmount;

    /**
     * 渠道回单的金额
     */
    private BigDecimal channelAmount;

    /**
     * 处理状态，0 未处理 1 已核销
     */
    private Integer handleStatus;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
