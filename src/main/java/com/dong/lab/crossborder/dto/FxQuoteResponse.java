package com.dong.lab.crossborder.dto;

import com.dong.lab.crossborder.entity.FxQuote;
import com.dong.lab.crossborder.enums.FxQuoteStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 汇率报价响应。expired 与 validSeconds 由服务端按当前时间计算，
 * 客户端不再自行判断有效期，避免客户端时钟偏差导致拿过期报价去成交。
 */
@Data
public class FxQuoteResponse {

    /**
     * 报价编号，用于后续成交时锁定该报价。
     */
    private String quoteNo;

    /**
     * 货币对，例如 USD/CNY。
     */
    private String currencyPair;

    /**
     * 买入价，渠道愿意买入基础货币的价格。
     */
    private BigDecimal bidRate;

    /**
     * 卖出价，渠道愿意卖出基础货币的价格。
     */
    private BigDecimal askRate;

    /**
     * 锁定汇率，客户确认报价后按此汇率成交。
     */
    private BigDecimal lockedRate;

    /**
     * 报价状态，例如有效、已锁定、已过期。
     */
    private FxQuoteStatus status;

    /**
     * 报价过期时间，超过该时间后报价失效。
     */
    private LocalDateTime expireTime;

    /**
     * 报价是否已过期，由服务端按当前时间计算后返回。
     */
    private boolean expired;

    /**
     * 剩余有效秒数，客户端可直接展示倒计时，不再自行计算。
     */
    private long validSeconds;

    public static FxQuoteResponse from(FxQuote entity) {
        FxQuoteResponse response = new FxQuoteResponse();
        response.setQuoteNo(entity.getQuoteNo());
        response.setCurrencyPair(entity.getCurrencyPair());
        response.setBidRate(entity.getBidRate());
        response.setAskRate(entity.getAskRate());
        response.setLockedRate(entity.getLockedRate());
        response.setStatus(entity.getStatus());
        response.setExpireTime(entity.getExpireTime());
        long seconds = java.time.Duration.between(LocalDateTime.now(), entity.getExpireTime()).getSeconds();
        response.setExpired(seconds <= 0);
        response.setValidSeconds(Math.max(0L, seconds));
        return response;
    }

}
