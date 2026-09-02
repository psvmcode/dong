package com.dong.lab.crossborder.service.impl;

import com.dong.lab.crossborder.entity.CrossBorderRemittance;
import com.dong.lab.crossborder.enums.RemittanceStatus;
import com.dong.lab.crossborder.mapper.CrossBorderRemittanceMapper;
import com.dong.lab.crossborder.service.FxExposureService;
import com.dong.lab.crossborder.service.FxQuoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 敞口监控实现。取已锁汇但尚未清算完成的汇款单，
 * 按货币对汇总锁定量，用当前中间价重估并与锁定汇率比对得出浮动盈亏。
 *
 * <p>盈亏的口径：客户汇出 1000 锁定汇率 0.14，银行需在清算时按市价购入 140 交付。
 * 若市价涨到 0.145，购入成本变高，银行浮亏。因此
 * 浮动盈亏 = 锁定量 ×（锁定汇率 − 当前市价），正为盈利、负为亏损。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FxExposureServiceImpl implements FxExposureService {

    /**
     * 敞口预警线。单货币对锁定量超过该值提示平盘，
     * 真实银行的额度按品种与期限分层管理，这里取一个演示值。
     */
    private static final BigDecimal EXPOSURE_ALERT = new BigDecimal("50000");

    private final CrossBorderRemittanceMapper remittanceMapper;

    private final FxQuoteService fxQuoteService;

    @Override
    public List<Map<String, Object>> exposureByPair() {
        List<CrossBorderRemittance> open = new ArrayList<>();
        open.addAll(remittanceMapper.selectByStatus(RemittanceStatus.QUOTE_LOCKED, 200));
        open.addAll(remittanceMapper.selectByStatus(RemittanceStatus.FUNDS_DEBITED, 200));
        open.addAll(remittanceMapper.selectByStatus(RemittanceStatus.SETTLING, 200));
        Map<String, List<CrossBorderRemittance>> byPair = new LinkedHashMap<>();
        for (CrossBorderRemittance item : open) {
            byPair.computeIfAbsent(item.getSourceCurrency() + "/" + item.getTargetCurrency(),
                    k -> new ArrayList<>()).add(item);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, List<CrossBorderRemittance>> entry : byPair.entrySet()) {
            String pair = entry.getKey();
            List<CrossBorderRemittance> items = entry.getValue();
            BigDecimal lockedNotional = BigDecimal.ZERO;
            BigDecimal weightedRate = BigDecimal.ZERO;
            for (CrossBorderRemittance item : items) {
                lockedNotional = lockedNotional.add(item.getSourceAmount());
                weightedRate = weightedRate.add(item.getExchangeRate().multiply(item.getSourceAmount()));
            }
            BigDecimal avgLockedRate = lockedNotional.signum() == 0 ? BigDecimal.ZERO
                    : weightedRate.divide(lockedNotional, 8, RoundingMode.HALF_UP);
            String[] parts = pair.split("/");
            BigDecimal current = fxQuoteService.currentRate(parts[0], parts[1]);
            BigDecimal floating = lockedNotional.multiply(avgLockedRate.subtract(current))
                    .setScale(2, RoundingMode.HALF_UP);
            boolean alert = lockedNotional.compareTo(EXPOSURE_ALERT) > 0;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("currencyPair", pair);
            row.put("openCount", items.size());
            row.put("lockedNotional", lockedNotional);
            row.put("avgLockedRate", avgLockedRate);
            row.put("currentRate", current);
            row.put("floatingPnl", floating);
            row.put("alert", alert);
            row.put("alertLine", EXPOSURE_ALERT);
            rows.add(row);
        }
        return rows;
    }

    @Override
    public Map<String, Object> summary() {
        List<Map<String, Object>> rows = exposureByPair();
        BigDecimal totalNotional = BigDecimal.ZERO;
        BigDecimal totalPnl = BigDecimal.ZERO;
        int alertPairs = 0;
        for (Map<String, Object> row : rows) {
            totalNotional = totalNotional.add((BigDecimal) row.get("lockedNotional"));
            totalPnl = totalPnl.add((BigDecimal) row.get("floatingPnl"));
            if (Boolean.TRUE.equals(row.get("alert"))) {
                alertPairs++;
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("pairCount", rows.size());
        summary.put("totalNotional", totalNotional);
        summary.put("totalFloatingPnl", totalPnl);
        summary.put("alertPairs", alertPairs);
        summary.put("rows", rows);
        return summary;
    }

}
