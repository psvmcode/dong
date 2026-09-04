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

    /**
     * 主键
     */
    private Long id;

    /**
     * 批次号，全局唯一
     */
    private String batchNo;

    /**
     * 清算渠道
     */
    private SettlementChannel channel;

    /**
     * 清算币种，同批次只能是一种币
     */
    private String currency;

    /**
     * 批次内汇款笔数
     */
    private Integer totalCount;

    /**
     * 批次内汇款总金额
     */
    private BigDecimal totalAmount;

    /**
     * 批次状态，1 收集中 2 已关闭 3 已清算
     */
    private SettlementStatus status;

    /**
     * 清算截止时间，渠道按窗口批量清算
     */
    private LocalDateTime cutoffTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
