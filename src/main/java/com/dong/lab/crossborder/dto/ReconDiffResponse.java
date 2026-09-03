package com.dong.lab.crossborder.dto;

import com.dong.lab.crossborder.entity.ReconDiff;
import com.dong.lab.crossborder.enums.ReconDiffType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对账差异响应。diffAmount 为正表示渠道比本地多（长款），
 * 为负表示渠道比本地少（短款），零差异才是核销的前提。
 */
@Data
public class ReconDiffResponse {

    /**
     * 对账批次号，标识这一轮对账的批次。
     */
    private String batchNo;

    /**
     * 关联的汇款单号，定位到具体哪笔汇款出现差异。
     */
    private String remittanceNo;

    /**
     * 差异类型，例如金额不一致、状态不一致、单边账等。
     */
    private ReconDiffType diffType;

    /**
     * 本地记录的金额。
     */
    private BigDecimal localAmount;

    /**
     * 渠道返回的金额。
     */
    private BigDecimal channelAmount;

    /**
     * 差异金额，由渠道金额减去本地金额计算，正数为长款、负数为短款。
     */
    private BigDecimal diffAmount;

    /**
     * 处理状态，记录差异是否已被认领、核销或忽略。
     */
    private Integer handleStatus;

    /**
     * 差异发现时间。
     */
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
