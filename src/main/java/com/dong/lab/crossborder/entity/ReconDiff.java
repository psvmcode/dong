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

    private String batchNo;

    private String remittanceNo;

    private ReconDiffType diffType;

    private BigDecimal localAmount;

    private BigDecimal channelAmount;

    private Integer handleStatus;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
