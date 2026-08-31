package com.dong.lab.crossborder.dto;

import com.dong.lab.crossborder.entity.ReconDiff;
import com.dong.lab.crossborder.enums.ReconDiffType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ReconDiffResponse {

    private String batchNo;

    private String remittanceNo;

    private ReconDiffType diffType;

    private BigDecimal localAmount;

    private BigDecimal channelAmount;

    private BigDecimal diffAmount;

    private Integer handleStatus;

    private LocalDateTime createTime;

    public static ReconDiffResponse from(ReconDiff entity) {
        ReconDiffResponse response = new ReconDiffResponse();
        response.setBatchNo(entity.getBatchNo());
        response.setRemittanceNo(entity.getRemittanceNo());
        response.setDiffType(entity.getDiffType());
        response.setLocalAmount(entity.getLocalAmount());
        response.setChannelAmount(entity.getChannelAmount());
        response.setDiffAmount(entity.getChannelAmount().subtract(entity.getLocalAmount()));
        response.setHandleStatus(entity.getHandleStatus());
        response.setCreateTime(entity.getCreateTime());
        return response;
    }

}
