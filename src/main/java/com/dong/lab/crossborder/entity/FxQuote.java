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

    /** 报价号，锁汇时引用 */
    private String quoteNo;

    /** 货币对，如 CNYUSD */
    private String currencyPair;

    /** 银行买入价 */
    private BigDecimal bidRate;

    /** 银行卖出价，客户换汇按此成交 */
    private BigDecimal askRate;

    /** 实际锁定的成交汇率，锁定后不再随市场波动 */
    private BigDecimal lockedRate;

    /** 报价状态：有效、已锁定、已使用、已过期 */
    private FxQuoteStatus status;

    /** 锁汇有效期截止时刻 */
    private LocalDateTime expireTime;

    /** 锁定该报价的汇款单号，未锁定为空 */
    private String remittanceNo;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
