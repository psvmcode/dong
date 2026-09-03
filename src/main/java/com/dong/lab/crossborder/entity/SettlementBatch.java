package com.dong.lab.crossborder.entity;

import com.dong.lab.crossborder.enums.SettlementChannel;
import com.dong.lab.crossborder.enums.SettlementStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 清算批次。cutoffTime 是清算截止时间，渠道每天有固定的清算窗口，
 * 错过就要等下一个窗口，这是跨境汇款到账时间差异的主要原因之一。
 */
@Data
public class SettlementBatch {

    private Long id;

    /** 批次号，渠道清算与对账都以批次为单位 */
    private String batchNo;

    /** 清算渠道：CIPS、SWIFT、CORRESPONDENT */
    private SettlementChannel channel;

    /** 批次币种，同币种的单子才能进同一批 */
    private String currency;

    /** 批次内汇款单数 */
    private Integer totalCount;

    /** 批次总金额，与渠道回单核对 */
    private BigDecimal totalAmount;

    /** 批次状态：收集、已报文、已清算、已关闭 */
    private SettlementStatus status;

    /** 本批清算截止时刻，错过等下一窗口 */
    private LocalDateTime cutoffTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
