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

    private String batchNo;

    private SettlementChannel channel;

    private String currency;

    private Integer totalCount;

    private BigDecimal totalAmount;

    private SettlementStatus status;

    private LocalDateTime cutoffTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

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
