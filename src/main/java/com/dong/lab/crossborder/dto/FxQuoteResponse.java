package com.dong.lab.crossborder.dto;

import com.dong.lab.crossborder.entity.FxQuote;
import com.dong.lab.crossborder.enums.FxQuoteStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FxQuoteResponse {

    private String quoteNo;

    private String currencyPair;

    private BigDecimal bidRate;

    private BigDecimal askRate;

    private BigDecimal lockedRate;

    private FxQuoteStatus status;

    private LocalDateTime expireTime;

    private boolean expired;

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
