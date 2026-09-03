package com.dong.lab.crossborder.entity;

import com.dong.lab.crossborder.enums.ReconDiffType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对账差异。每日对账把渠道回单与本地流水逐笔比对，
 * 比不上的都记到这里，由运营按差异类型处理。
 */
@Data
public class ReconDiff {

    private Long id;

    /** 所属清算批次 */
    private String batchNo;

    /** 有差异的汇款单号，单边账时可能为空 */
    private String remittanceNo;

    /** 差异类型：长款、短款、单边账、金额不符 */
    private ReconDiffType diffType;

    /** 本地账务金额 */
    private BigDecimal localAmount;

    /** 渠道回单金额 */
    private BigDecimal channelAmount;

    /** 处理状态：待处理、已核销、已挂账 */
    private Integer handleStatus;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
