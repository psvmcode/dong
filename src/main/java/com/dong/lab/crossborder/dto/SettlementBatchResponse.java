package com.dong.lab.crossborder.dto;

import com.dong.lab.crossborder.entity.SettlementBatch;
import com.dong.lab.crossborder.enums.SettlementChannel;
import com.dong.lab.crossborder.enums.SettlementStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 清算批次响应。渠道按批次清算，cutoffTime 是本批的截止时刻，
 * 之后进来的单子只能等下一批，跨境资金的时效由批次节奏决定。
 */
@Data

public class SettlementBatchResponse {

    /**
     * 清算批次号，标识本轮清算批次。
     */
    private String batchNo;

    /**
     * 清算渠道。
     */
    private SettlementChannel channel;

    /**
     * 批次币种。
     */
    private String currency;

    /**
     * 批次内汇款总笔数。
     */
    private Integer totalCount;

    /**
     * 批次内汇款总金额。
     */
    private BigDecimal totalAmount;

    /**
     * 批次状态，例如待清算、清算中、已完成、失败。
     */
    private SettlementStatus status;

    /**
     * 截单时间，之后进入的汇款只能等待下一批次。
     */
    private LocalDateTime cutoffTime;

    /**
     * 批次创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 批次最后更新时间。
     */
    private LocalDateTime updateTime;

    /**
     * 从实体转换为 DTO。
     */
    public static SettlementBatchResponse from(SettlementBatch entity) {
        SettlementBatchResponse response = new SettlementBatchResponse();
        response.setBatchNo(entity.getBatchNo());
        response.setChannel(entity.getChannel());
        response.setCurrency(entity.getCurrency());
        response.setTotalCount(entity.getTotalCount());
        response.setTotalAmount(entity.getTotalAmount());
        response.setStatus(entity.getStatus());
        response.setCutoffTime(entity.getCutoffTime());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }

}
