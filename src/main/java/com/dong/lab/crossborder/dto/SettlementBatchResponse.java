package com.dong.lab.crossborder.dto;

import com.dong.lab.crossborder.entity.SettlementBatch;
import com.dong.lab.crossborder.enums.SettlementChannel;
import com.dong.lab.crossborder.enums.SettlementStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
