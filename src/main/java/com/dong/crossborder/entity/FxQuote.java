package com.dong.crossborder.entity;

import com.dong.crossborder.enums.FxQuoteStatus;
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

    /**
     * 主键
     */
    private Long id;

    /**
     * 报价单号，汇款时引用它锁定汇率
     */
    private String quoteNo;

    /**
     * 货币对，格式为 源币种/目标币种
     */
    private String currencyPair;

    /**
     * 银行买入价
     */
    private BigDecimal bidRate;

    /**
     * 银行卖出价，客户换汇按此价成交
     */
    private BigDecimal askRate;

    /**
     * 锁定汇率，锁定成功后等于卖出价
     */
    private BigDecimal lockedRate;

    /**
     * 报价状态，1 可用 2 已锁定 3 已使用 4 已过期
     */
    private FxQuoteStatus status;

    /**
     * 报价有效期截止时间，过期必须重新询价
     */
    private LocalDateTime expireTime;

    /**
     * 锁定该报价的汇款单号
     */
    private String remittanceNo;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
