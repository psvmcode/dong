package com.dong.lab.crossborder.service.impl;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import com.dong.lab.common.util.Snowflake;
import com.dong.lab.crossborder.dto.FxQuoteResponse;
import com.dong.lab.crossborder.entity.FxQuote;
import com.dong.lab.crossborder.enums.FxQuoteStatus;
import com.dong.lab.crossborder.enums.SettlementChannel;
import com.dong.lab.crossborder.mapper.FxQuoteMapper;
import com.dong.lab.crossborder.service.FxQuoteService;
import com.dong.lab.framework.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 汇率报价实现。
 *
 * <p>汇率以美元为中间货币做交叉计算，这是外汇市场的通行做法：
 * 任意两个币种的汇率由各自对美元的中间价推导，避免维护全量货币对的牌价。
 *
 * <p>点差是银行的收益来源，bid 与 ask 之间的差额即利润。
 * 客户换汇按 ask 成交，因此实际到手金额会略少于按中间价计算的结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FxQuoteServiceImpl implements FxQuoteService {

    /**
     * 各币种对美元的中间价。真实系统由风控或交易系统实时推送，
     * 这里用静态表模拟，重点是展示锁汇机制本身。
     */
    private static final Map<String, BigDecimal> USD_RATES = Map.of(
            "USD", new BigDecimal("1.0000"),
            "CNY", new BigDecimal("7.1500"),
            "EUR", new BigDecimal("0.9200"),
            "JPY", new BigDecimal("150.0000"),
            "HKD", new BigDecimal("7.8000"),
            "GBP", new BigDecimal("0.7900"));

    /**
     * 点差，买卖价之间的差额比例。
     */
    private static final BigDecimal SPREAD = new BigDecimal("0.003");

    private static final String RATE_CACHE_PREFIX = "lab:crossborder:rate:";

    private static final Duration RATE_CACHE_TTL = Duration.ofSeconds(30);

    private final FxQuoteMapper fxQuoteMapper;

    private final RedisService redisService;

    private final Snowflake snowflake;

    @Override
    public FxQuoteResponse quote(String sourceCurrency, String targetCurrency, long validSeconds) {
        if (sourceCurrency.equals(targetCurrency)) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "source and target currency must differ");
        }
        BigDecimal mid = midRate(sourceCurrency, targetCurrency);
        BigDecimal ask = mid.multiply(BigDecimal.ONE.add(SPREAD.divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP)))
                .setScale(8, RoundingMode.HALF_UP);
        BigDecimal bid = mid.multiply(BigDecimal.ONE.subtract(SPREAD.divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP)))
                .setScale(8, RoundingMode.HALF_UP);
        FxQuote quote = new FxQuote();
        quote.setQuoteNo("FQ" + snowflake.nextId());
        quote.setCurrencyPair(sourceCurrency + "/" + targetCurrency);
        quote.setBidRate(bid);
        quote.setAskRate(ask);
        quote.setLockedRate(BigDecimal.ZERO);
        quote.setStatus(FxQuoteStatus.AVAILABLE);
        quote.setExpireTime(LocalDateTime.now().plusSeconds(validSeconds));
        quote.setRemittanceNo("");
        fxQuoteMapper.insert(quote);
        return FxQuoteResponse.from(quote);
    }

    @Override
    public FxQuoteResponse findByQuoteNo(String quoteNo) {
        FxQuote quote = fxQuoteMapper.selectByQuoteNo(quoteNo);
        if (quote == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "quote " + quoteNo + " not found");
        }
        return FxQuoteResponse.from(quote);
    }

    /**
     * 锁定报价。update 语句带 status = 1 条件形成乐观锁，
     * 并发锁定同一报价时只有一个能更新成功，其余会被判定为失效。
     */
    @Override
    public BigDecimal lock(String quoteNo, String remittanceNo) {
        FxQuote quote = fxQuoteMapper.selectByQuoteNo(quoteNo);
        if (quote == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "quote " + quoteNo + " not found");
        }
        if (quote.getStatus() != FxQuoteStatus.AVAILABLE) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "quote " + quoteNo + " is not available, current status " + quote.getStatus());
        }
        if (quote.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "quote " + quoteNo + " already expired");
        }
        int updated = fxQuoteMapper.lock(quoteNo, remittanceNo, quote.getAskRate());
        if (updated <= 0) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "quote " + quoteNo + " was taken by another request");
        }
        return quote.getAskRate();
    }

    @Override
    public void markUsed(String quoteNo) {
        fxQuoteMapper.updateStatus(quoteNo, FxQuoteStatus.USED);
    }

    /**
     * 当前牌价。高频读取场景，用 Redis 缓存 30 秒，
     * 汇率本身变化不剧烈，短暂延迟不影响展示。
     */
    @Override
    public BigDecimal currentRate(String sourceCurrency, String targetCurrency) {
        String cacheKey = RATE_CACHE_PREFIX + sourceCurrency + ":" + targetCurrency;
        return redisService.get(cacheKey)
                .map(BigDecimal::new)
                .orElseGet(() -> {
                    BigDecimal rate = midRate(sourceCurrency, targetCurrency);
                    redisService.set(cacheKey, rate.toPlainString(), RATE_CACHE_TTL);
                    return rate;
                });
    }

    /**
     * 手续费按渠道区分。SWIFT 要经过代理行，成本最高；
     * CIPS 走人民币清算更便宜；本地清算成本最低。
     * 都由固定费加比例费构成，这是真实渠道的计费方式。
     */
    @Override
    public BigDecimal fee(BigDecimal sourceAmount, SettlementChannel channel) {
        if (channel == null) {
            channel = SettlementChannel.SWIFT;
        }
        return switch (channel) {
            case SWIFT -> new BigDecimal("50").add(sourceAmount.multiply(new BigDecimal("0.001")))
                    .setScale(2, RoundingMode.HALF_UP);
            case CIPS -> new BigDecimal("10").add(sourceAmount.multiply(new BigDecimal("0.0005")))
                    .setScale(2, RoundingMode.HALF_UP);
            case LOCAL -> new BigDecimal("5").add(sourceAmount.multiply(new BigDecimal("0.0002")))
                    .setScale(2, RoundingMode.HALF_UP);
        };
    }

    @Override
    public int expireOverdue() {
        return fxQuoteMapper.expireOverdue(LocalDateTime.now());
    }

    @Override
    public List<FxQuoteResponse> available(String currencyPair) {
        return fxQuoteMapper.selectByPairAndStatus(currencyPair, FxQuoteStatus.AVAILABLE, 20).stream()
                .map(FxQuoteResponse::from)
                .toList();
    }

    @Override
    public int clearAll() {
        return fxQuoteMapper.clearAll();
    }

    /**
     * 交叉汇率计算。源币种先换成美元，再由美元换成目标币种。
     */
    private BigDecimal midRate(String sourceCurrency, String targetCurrency) {
        BigDecimal sourceToUsd = USD_RATES.get(sourceCurrency);
        BigDecimal targetToUsd = USD_RATES.get(targetCurrency);
        if (sourceToUsd == null || targetToUsd == null) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID,
                    "unsupported currency pair " + sourceCurrency + "/" + targetCurrency);
        }
        return targetToUsd.divide(sourceToUsd, 8, RoundingMode.HALF_UP);
    }

}
