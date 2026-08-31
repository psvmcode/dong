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

    private String batchNo;

    private SettlementChannel channel;

    private String currency;

    private Integer totalCount;

    private BigDecimal totalAmount;

    private SettlementStatus status;

    private LocalDateTime cutoffTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
