package com.dong.lab.crossborder.dto;

import com.dong.lab.crossborder.entity.CrossBorderRemittance;
import com.dong.lab.crossborder.enums.RemittanceStatus;
import com.dong.lab.crossborder.enums.SettlementChannel;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 汇款单响应。返回给调用方的是脱敏后的视图：
 * 只带账号编号不带账户内部 id，金额三件套（源金额、汇率、目标金额）
 * 加上手续费，客户端可以完整复算这笔汇款的成本。
 */
@Data
public class RemittanceResponse {

    private String remittanceNo;

    private String idempotentKey;

    private String payerAccountNo;

    private String payeeAccountNo;

    private String sourceCurrency;

    private String targetCurrency;

    private BigDecimal sourceAmount;

    private BigDecimal exchangeRate;

    private BigDecimal targetAmount;

    private BigDecimal feeAmount;

    private SettlementChannel channel;

    private RemittanceStatus status;

    private String quoteNo;

    private String batchNo;

    private String failReason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static RemittanceResponse from(CrossBorderRemittance entity) {
        RemittanceResponse response = new RemittanceResponse();
        response.setRemittanceNo(entity.getRemittanceNo());
        response.setIdempotentKey(entity.getIdempotentKey());
        response.setSourceCurrency(entity.getSourceCurrency());
        response.setTargetCurrency(entity.getTargetCurrency());
        response.setSourceAmount(entity.getSourceAmount());
        response.setExchangeRate(entity.getExchangeRate());
        response.setTargetAmount(entity.getTargetAmount());
        response.setFeeAmount(entity.getFeeAmount());
        response.setChannel(entity.getChannel());
        response.setStatus(entity.getStatus());
        response.setQuoteNo(entity.getQuoteNo());
        response.setBatchNo(entity.getBatchNo());
        response.setFailReason(entity.getFailReason());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }

    public static RemittanceResponse from(CrossBorderRemittance entity, String payerAccountNo, String payeeAccountNo) {
        RemittanceResponse response = from(entity);
        response.setPayerAccountNo(payerAccountNo);
        response.setPayeeAccountNo(payeeAccountNo);
        return response;
    }

}
