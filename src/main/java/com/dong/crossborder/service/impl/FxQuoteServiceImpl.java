package com.dong.crossborder.service.impl;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.common.util.Snowflake;
import com.dong.crossborder.dto.FxQuoteResponse;
import com.dong.crossborder.entity.FxQuote;
import com.dong.crossborder.enums.FxQuoteStatus;
import com.dong.crossborder.enums.SettlementChannel;
import com.dong.crossborder.mapper.FxQuoteMapper;
import com.dong.crossborder.service.FxQuoteService;
import com.dong.framework.lock.DistributedLockService;
import com.dong.framework.redis.RedisService;
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
     * 点差，买卖价之间的差额比例。
     */
    private static final BigDecimal SPREAD = new BigDecimal("0.003");

    private static final String RATE_CACHE_PREFIX = "lab:crossborder:rate:";

    private static final Duration RATE_CACHE_TTL = Duration.ofSeconds(30);

    /**
     * fxQuoteMapper，MyBatis Mapper 数据访问层。
     */
    private final FxQuoteMapper fxQuoteMapper;

    /**
     * fxRateMapper。牌价从库里读，不再写死成常量：
     * 汇率是会变的经营数据，写死意味着每次调整都要改代码发版，
     * 也无法回答「这笔成交用的牌价是谁定的」。
     */
    private final com.dong.crossborder.mapper.FxRateMapper fxRateMapper;

    /**
     * channelConfigMapper。固定费与比例费按渠道取，与牌价同样要求可调整。
     */
    private final com.dong.crossborder.mapper.ChannelConfigMapper channelConfigMapper;

    /**
     * redisService，业务服务层。
     */
    private final RedisService redisService;

    /**
     * distributedLockService，业务服务层。
     */
    private final DistributedLockService distributedLockService;

    /**
     * snowflake。
     */
    private final Snowflake snowflake;

    /**
     * quote。
     */
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

    /**
     * findByQuoteNo。
     */
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

    /**
     * markUsed。
     */
    @Override
    public void markUsed(String quoteNo) {
        fxQuoteMapper.updateStatus(quoteNo, FxQuoteStatus.USED);
    }

    /**
     * 当前牌价。高频读取场景，用 Redis 缓存 30 秒，
     * 汇率本身变化不剧烈，短暂延迟不影响展示。
     *
     * <p>这里用分布式锁防止缓存击穿：缓存未命中时，
     * 并发请求里只有一个去回源计算，其余等待或读刚写入的缓存。
     */
    @Override
    public BigDecimal currentRate(String sourceCurrency, String targetCurrency) {
        String cacheKey = RATE_CACHE_PREFIX + sourceCurrency + ":" + targetCurrency;
        return redisService.get(cacheKey)
                .map(BigDecimal::new)
                .orElseGet(() -> {
                    try (var handle = distributedLockService.tryLock(
                            cacheKey + ":lock", Duration.ofSeconds(5), Duration.ofSeconds(2))) {
                        if (handle.isAcquired()) {
                            return redisService.get(cacheKey)
                                    .map(BigDecimal::new)
                                    .orElseGet(() -> {
                                        BigDecimal rate = midRate(sourceCurrency, targetCurrency);
                                        redisService.set(cacheKey, rate.toPlainString(), RATE_CACHE_TTL);
                                        return rate;
                                    });
                        }
                        return midRate(sourceCurrency, targetCurrency);
                    }
                });
    }

    /**
     * 手续费按渠道区分。SWIFT 要经过代理行，成本最高；
     * CIPS 走人民币清算更便宜；本地清算成本最低。
     * 都由固定费加比例费构成，费率取自渠道配置表以便运营调整。
     */
    @Override
    public BigDecimal fee(BigDecimal sourceAmount, SettlementChannel channel) {
        if (channel == null) {
            channel = SettlementChannel.SWIFT;
        }
        com.dong.crossborder.entity.ChannelConfig config =
                channelConfigMapper.selectByChannel(channel.getCode());
        if (config == null) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "channel not configured " + channel);
        }
        return config.getFixedFee()
                .add(sourceAmount.multiply(config.getRateFee()))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 批量标记过期报价。
     */
    @Override
    public int expireOverdue() {
        return fxQuoteMapper.expireOverdue(LocalDateTime.now());
    }

    /**
     * available。
     */
    @Override
    public List<FxQuoteResponse> available(String currencyPair) {
        return fxQuoteMapper.selectByPairAndStatus(currencyPair, FxQuoteStatus.AVAILABLE, 20).stream()
                .map(FxQuoteResponse::from)
                .toList();
    }

    /**
     * 清空全部数据，仅测试场景使用。
     */
    @Override
    public int clearAll() {
        return fxQuoteMapper.clearAll();
    }

    /**
     * 查询全部牌价。
     */
    @Override
    public List<com.dong.crossborder.dto.FxRateResponse> allRates() {
        return fxRateMapper.selectAll().stream()
                .map(com.dong.crossborder.dto.FxRateResponse::from)
                .toList();
    }

    /**
     * 调整牌价。真实系统由交易系统推送，这里提供手动调整的入口。
     */
    @Override
    public void updateRate(String currency, BigDecimal usdRate) {
        requireRate(currency);
        fxRateMapper.updateRate(currency, usdRate);
        // 中间价有缓存，改牌价后必须失效，否则询价仍在用旧价
        redisService.delete(RATE_CACHE_PREFIX + currency);
        log.info("fx rate updated currency={} usdRate={}", currency, usdRate);
    }

    /**
     * 支持的币种取自牌价表，牌价里没有的币种一律不接受。
     */
    @Override
    public java.util.Set<String> supportedCurrencies() {
        return fxRateMapper.selectAll().stream()
                .map(com.dong.crossborder.entity.FxRate::getCurrency)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 交叉汇率计算。源币种先换成美元，再由美元换成目标币种。
     */
    private BigDecimal midRate(String sourceCurrency, String targetCurrency) {
        BigDecimal sourceToUsd = requireRate(sourceCurrency).getUsdRate();
        BigDecimal targetToUsd = requireRate(targetCurrency).getUsdRate();
        return targetToUsd.divide(sourceToUsd, 8, RoundingMode.HALF_UP);
    }

    /**
     * 取指定币种的牌价，停用或不存在都按不支持处理。
     */
    private com.dong.crossborder.entity.FxRate requireRate(String currency) {
        com.dong.crossborder.entity.FxRate rate = fxRateMapper.selectByCurrency(currency);
        if (rate == null) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "unsupported currency " + currency);
        }
        return rate;
    }

}
