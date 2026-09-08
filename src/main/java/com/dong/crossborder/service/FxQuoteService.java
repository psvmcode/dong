package com.dong.crossborder.service;

import com.dong.crossborder.dto.FxQuoteResponse;
import com.dong.crossborder.dto.FxRateResponse;
import com.dong.crossborder.enums.SettlementChannel;

import java.math.BigDecimal;
import java.util.List;

/**
 * 汇率报价与锁汇。
 *
 * <p>锁汇是跨境支付的核心概念：询价时银行给出一个汇率并承诺在有效期内按此成交，
 * 客户在有效期内完成汇款即锁定该汇率，不受期间市场波动影响。
 * 报价过期必须重新询价，否则银行要承担汇率风险。
 */
public interface FxQuoteService {

    /**
     * 询价，返回一笔带有效期的报价。
     */
    FxQuoteResponse quote(String sourceCurrency, String targetCurrency, long validSeconds);

    /**
     * findByQuoteNo。
     */
    FxQuoteResponse findByQuoteNo(String quoteNo);

    /**
     * 锁定报价。带乐观锁，并发锁定同一报价只有一个能成功。
     *
     * @return 锁定后的汇率
     */
    BigDecimal lock(String quoteNo, String remittanceNo);

    /**
     * 标记报价已使用。
     */
    void markUsed(String quoteNo);

    /**
     * 当前牌价，用于展示与对比锁定汇率。
     */
    BigDecimal currentRate(String sourceCurrency, String targetCurrency);

    /**
     * 按渠道计算手续费。不同渠道成本差异明显，SWIFT 最贵。
     */
    BigDecimal fee(BigDecimal sourceAmount, SettlementChannel channel);

    /**
     * 批量标记过期报价，由定时任务调用。
     */
    int expireOverdue();

    /**
     * available。
     */
    List<FxQuoteResponse> available(String currencyPair);

    /**
     * 支持的币种集合。开户与询价都要用它校验输入：
     * 不接受任意字符串，否则会开出永远无法汇款的账户。
     */
    java.util.Set<String> supportedCurrencies();

    /**
     * 查询全部牌价。
     *
     * @return 牌价列表
     */
    java.util.List<FxRateResponse> allRates();

    /**
     * 调整某个币种的牌价，同时失效中间价缓存。
     *
     * @param currency 币种
     * @param usdRate  一美元兑换该币种的数量
     */
    void updateRate(String currency, BigDecimal usdRate);

    /**
     * 清空全部数据，仅测试场景使用。
     */
    int clearAll();

}
