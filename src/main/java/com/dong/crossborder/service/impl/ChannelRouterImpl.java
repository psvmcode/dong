package com.dong.crossborder.service.impl;

import com.dong.crossborder.entity.ChannelConfig;
import com.dong.crossborder.enums.SettlementChannel;
import com.dong.crossborder.service.ChannelRouter;
import com.dong.crossborder.service.FxQuoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * 渠道路由实现。对每个可用渠道计算总成本并叠加时效权重后择优。
 *
 * <p>渠道特性来自真实市场：SWIFT 覆盖广但贵且慢，
 * CIPS 走人民币清算便宜且快，LOCAL 本地网络最便宜但单笔额度小。
 * LOCAL 因此有一个硬性单笔上限，超限直接从候选里剔除。
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class ChannelRouterImpl implements ChannelRouter {

    /**
     * 时效权重。加急时每多等一小时折算的成本惩罚会显著放大。
     */
    private static final BigDecimal ETA_WEIGHT = new BigDecimal("0.01");

    /**
     * 加急时的时效权重，是普通权重的五倍。
     * 「加急要加钱」就体现在这里：慢渠道的时效成本被放大五倍后会被迅速淘汰。
     */
    private static final BigDecimal URGENT_ETA_WEIGHT = new BigDecimal("0.05");

    /**
     * fxQuoteService，业务服务层。
     */
    private final FxQuoteService fxQuoteService;

    /**
     * channelConfigMapper。渠道的时效、上限与费率都从库里读：
     * 代理行调价、监管改额度、线路故障都是会发生的事，
     * 写死成常量就只能靠发版应对。
     */
    private final com.dong.crossborder.mapper.ChannelConfigMapper channelConfigMapper;

    /**
     * route。
     */
    @Override
    public RouteDecision route(BigDecimal sourceAmount, boolean urgent) {
        BigDecimal weight = urgent ? URGENT_ETA_WEIGHT : ETA_WEIGHT;
        List<String> reasons = new ArrayList<>();
        SettlementChannel best = null;
        BigDecimal bestScore = null;
        BigDecimal bestFee = null;
        // 只遍历启用渠道，停用的渠道等同熔断，不能作为候选
        for (ChannelConfig config : channelConfigMapper.selectEnabled()) {
            SettlementChannel channel = SettlementChannel.of(config.getChannel());
            if (sourceAmount.compareTo(config.getPerTxLimit()) > 0) {
                reasons.add(channel + " excluded, amount exceeds per tx limit " + config.getPerTxLimit());
                continue;
            }
            BigDecimal fee = fxQuoteService.fee(sourceAmount, channel);
            BigDecimal etaCost = BigDecimal.valueOf(config.getEtaMinutes())
                    .multiply(weight)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal score = fee.add(etaCost);
            reasons.add(channel + " fee=" + fee + " etaCost=" + etaCost + " score=" + score);
            if (bestScore == null || score.compareTo(bestScore) < 0) {
                best = channel;
                bestScore = score;
                bestFee = fee;
            }
        }
        if (best == null) {
            best = SettlementChannel.SWIFT;
            bestFee = fxQuoteService.fee(sourceAmount, best);
            reasons.add("no enabled channel qualified, fallback to " + best);
        }
        return new RouteDecision(best, bestFee, List.copyOf(reasons));
    }

    /**
     * 全渠道评分明细，供前端展示与人工核对。
     */
    @Override
    public Map<String, Object> scoreAll(BigDecimal sourceAmount, boolean urgent) {
        BigDecimal weight = urgent ? URGENT_ETA_WEIGHT : ETA_WEIGHT;
        Map<String, Object> scores = new LinkedHashMap<>();
        for (ChannelConfig config : channelConfigMapper.selectAll()) {
            SettlementChannel channel = SettlementChannel.of(config.getChannel());
            // 停用渠道展示出来但标记为不合格，便于运营看到熔断状态
            boolean qualified = config.getEnabled() == 1
                    && sourceAmount.compareTo(config.getPerTxLimit()) <= 0;
            BigDecimal fee = fxQuoteService.fee(sourceAmount, channel);
            BigDecimal etaCost = BigDecimal.valueOf(config.getEtaMinutes())
                    .multiply(weight)
                    .setScale(2, RoundingMode.HALF_UP);
            scores.put(channel.name(), Map.of(
                    "qualified", qualified,
                    "enabled", config.getEnabled() == 1,
                    "fee", fee,
                    "etaMinutes", config.getEtaMinutes(),
                    "etaCost", etaCost,
                    "score", fee.add(etaCost),
                    "perTxLimit", config.getPerTxLimit()));
        }
        return scores;
    }

}
