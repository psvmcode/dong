package com.dong.lab.crossborder.entity;

import com.dong.lab.crossborder.enums.FxQuoteStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 汇率报价。bid 是银行买入价、ask 是银行卖出价，客户换汇用 ask，
 * 两者之间的点差就是银行的收益来源。
 * expireTime 是锁汇有效期，超过则报价失效必须重新询价。
 */
@Data
public class FxQuote {

    private Long id;

    private String quoteNo;

    private String currencyPair;

    private BigDecimal bidRate;

    private BigDecimal askRate;

    private BigDecimal lockedRate;

    private FxQuoteStatus status;

    private LocalDateTime expireTime;

    private String remittanceNo;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
