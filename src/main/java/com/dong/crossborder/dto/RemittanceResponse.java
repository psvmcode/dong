package com.dong.crossborder.dto;

import com.dong.crossborder.entity.CrossBorderRemittance;
import com.dong.crossborder.enums.RemittanceStatus;
import com.dong.crossborder.enums.SettlementChannel;
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

    /**
     * 汇款单号，对外展示的唯一标识。
     */
    private String remittanceNo;

    /**
     * 幂等键，用于调用方识别同一笔业务请求。
     */
    private String idempotentKey;

    /**
     * 付款账户编号。
     */
    private String payerAccountNo;

    /**
     * 收款账户编号。
     */
    private String payeeAccountNo;

    /**
     * 源币种，即汇款发起时的币种。
     */
    private String sourceCurrency;

    /**
     * 目标币种，即收款方期望收到的币种。
     */
    private String targetCurrency;

    /**
     * 源币种金额。
     */
    private BigDecimal sourceAmount;

    /**
     * 成交汇率，用于复算目标金额。
     */
    private BigDecimal exchangeRate;

    /**
     * 目标币种金额，由源金额乘以汇率计算得出。
     */
    private BigDecimal targetAmount;

    /**
     * 手续费金额，客户实际支付的汇款成本之一。
     */
    private BigDecimal feeAmount;

    /**
     * 实际使用的清算渠道。
     */
    private SettlementChannel channel;

    /**
     * 汇款状态，例如待处理、成功、失败、复核中等。
     */
    private RemittanceStatus status;

    /**
     * 锁价报价编号。
     */
    private String quoteNo;

    /**
     * 清算批次号，标识该笔汇款归属的清算批次。
     */
    private String batchNo;

    /**
     * 失败原因，仅在状态为失败时有效。
     */
    private String failReason;

    /**
     * 汇款单创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 汇款单最后更新时间。
     */
    private LocalDateTime updateTime;

    /**
     * 从实体转换为 DTO。
     */
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

    /**
     * 从实体转换为 DTO。
     */
    public static RemittanceResponse from(CrossBorderRemittance entity, String payerAccountNo, String payeeAccountNo) {
        RemittanceResponse response = from(entity);
        response.setPayerAccountNo(payerAccountNo);
        response.setPayeeAccountNo(payeeAccountNo);
        return response;
    }

}
