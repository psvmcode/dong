package com.dong.lab.crossborder.service.impl;

import com.dong.lab.crossborder.enums.SettlementChannel;
import com.dong.lab.crossborder.service.ChannelRouter;
import com.dong.lab.crossborder.service.FxQuoteService;
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
     * 渠道平均到账分钟数，用于时效打分。
     */
    private static final Map<SettlementChannel, Long> ETA_MINUTES = Map.of(
            SettlementChannel.SWIFT, 2880L,
            SettlementChannel.CIPS, 60L,
            SettlementChannel.LOCAL, 30L);

    /**
     * 渠道单笔上限。LOCAL 依托各国本地清算网络，
     * 大额要拆分或走其他渠道，这是真实存在的额度约束。
     */
    private static final Map<SettlementChannel, BigDecimal> PER_TX_LIMIT = Map.of(
            SettlementChannel.SWIFT, new BigDecimal("100000000"),
            SettlementChannel.CIPS, new BigDecimal("50000000"),
            SettlementChannel.LOCAL, new BigDecimal("300000"));

    /**
     * 时效权重。加急时每多等一小时折算的成本惩罚会显著放大。
     */
    private static final BigDecimal ETA_WEIGHT = new BigDecimal("0.01");

    private static final BigDecimal URGENT_ETA_WEIGHT = new BigDecimal("0.05");

    /**
     * fxQuoteService，业务服务层。
     */
    private final FxQuoteService fxQuoteService;

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
        for (SettlementChannel channel : SettlementChannel.values()) {
            BigDecimal limit = PER_TX_LIMIT.get(channel);
            if (sourceAmount.compareTo(limit) > 0) {
                reasons.add(channel + " excluded, amount exceeds per tx limit " + limit);
                continue;
            }
            BigDecimal fee = fxQuoteService.fee(sourceAmount, channel);
            BigDecimal etaCost = BigDecimal.valueOf(ETA_MINUTES.get(channel))
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
            reasons.add("no channel qualified, fallback to " + best);
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
        for (SettlementChannel channel : SettlementChannel.values()) {
            BigDecimal limit = PER_TX_LIMIT.get(channel);
            boolean qualified = sourceAmount.compareTo(limit) <= 0;
            BigDecimal fee = fxQuoteService.fee(sourceAmount, channel);
            BigDecimal etaCost = BigDecimal.valueOf(ETA_MINUTES.get(channel))
                    .multiply(weight)
                    .setScale(2, RoundingMode.HALF_UP);
            scores.put(channel.name(), Map.of(
                    "qualified", qualified,
                    "fee", fee,
                    "etaMinutes", ETA_MINUTES.get(channel),
                    "etaCost", etaCost,
                    "score", fee.add(etaCost),
                    "perTxLimit", limit));
        }
        return scores;
    }

}
