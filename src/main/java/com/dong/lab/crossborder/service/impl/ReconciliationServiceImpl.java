package com.dong.lab.crossborder.service.impl;

import com.dong.lab.crossborder.dto.ReconDiffResponse;
import com.dong.lab.crossborder.dto.ReconReportResponse;
import com.dong.lab.crossborder.entity.CrossBorderRemittance;
import com.dong.lab.crossborder.entity.ReconDiff;
import com.dong.lab.crossborder.entity.SettlementBatch;
import com.dong.lab.crossborder.enums.ReconDiffType;
import com.dong.lab.crossborder.enums.RemittanceStatus;
import com.dong.lab.crossborder.enums.SettlementStatus;
import com.dong.lab.crossborder.mapper.CrossBorderReconMapper;
import com.dong.lab.crossborder.mapper.ReconDiffMapper;
import com.dong.lab.crossborder.mapper.SettlementBatchMapper;
import com.dong.lab.crossborder.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 对账服务实现。
 *
 * <p>对账流程：
 * 取本地已结算的汇款单作为基准，模拟渠道回单，逐笔按汇款单号匹配，
 * 匹配上则比对金额，匹配不上按方向分别记为"本地有渠道无"或"渠道有本地无"。
 *
 * <p>模拟差错是为了测试对账逻辑本身：
 * 真实渠道也会因为系统故障、网络问题或人工操作出错而下发不准确的回单，
 * 对账系统必须能识别出这些差异。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationServiceImpl implements ReconciliationService {

    private final ReconDiffMapper reconDiffMapper;

    private final CrossBorderReconMapper reconMapper;

    private final SettlementBatchMapper batchMapper;

    @Override
    public ReconReportResponse reconcile(String batchNo) {
        SettlementBatch batch = batchMapper.selectByBatchNo(batchNo);
        if (batch == null) {
            throw new IllegalArgumentException("batch " + batchNo + " not found");
        }
        if (batch.getStatus() != SettlementStatus.SETTLED) {
            throw new IllegalStateException("batch " + batchNo + " is not settled, cannot reconcile");
        }
        List<CrossBorderRemittance> localItems = reconMapper.selectSettledByBatch(batchNo, RemittanceStatus.SETTLED);
        List<Map<String, Object>> channelStatement = generateChannelStatement(batchNo, 0.0);
        Map<String, Map<String, Object>> channelByNo = new HashMap<>();
        for (Map<String, Object> item : channelStatement) {
            channelByNo.put(String.valueOf(item.get("remittanceNo")), item);
        }
        List<ReconDiff> diffs = new ArrayList<>();
        int matched = 0;
        for (CrossBorderRemittance local : localItems) {
            Map<String, Object> channel = channelByNo.get(local.getRemittanceNo());
            if (channel == null) {
                diffs.add(buildDiff(batchNo, local.getRemittanceNo(), ReconDiffType.MISSING_IN_CHANNEL,
                        local.getTargetAmount(), BigDecimal.ZERO));
                continue;
            }
            BigDecimal channelAmount = new BigDecimal(String.valueOf(channel.get("amount")));
            if (local.getTargetAmount().compareTo(channelAmount) == 0) {
                matched++;
            } else {
                diffs.add(buildDiff(batchNo, local.getRemittanceNo(), ReconDiffType.AMOUNT_MISMATCH,
                        local.getTargetAmount(), channelAmount));
            }
            channelByNo.remove(local.getRemittanceNo());
        }
        for (Map<String, Object> extra : channelByNo.values()) {
            BigDecimal channelAmount = new BigDecimal(String.valueOf(extra.get("amount")));
            diffs.add(buildDiff(batchNo, String.valueOf(extra.get("remittanceNo")), ReconDiffType.MISSING_IN_LOCAL,
                    BigDecimal.ZERO, channelAmount));
        }
        if (!diffs.isEmpty()) {
            reconDiffMapper.batchInsert(diffs);
        }
        log.info("reconciliation finished batchNo={} local={} channel={} matched={} diffs={}",
                batchNo, localItems.size(), channelStatement.size(), matched, diffs.size());
        return buildReport(batch, localItems, channelStatement, matched, diffs);
    }

    /**
     * 模拟渠道回单。基于本地已结算汇款单生成，
     * 按 errorRate 引入三类差错：
     * 金额偏移（让部分单子的金额与本地不一致，模拟中间行扣费或汇率精度差异）；
     * 漏单（随机丢弃部分单子，模拟渠道漏报）；
     * 多余单（随机插入本地没有的单子，模拟渠道多放款或串单）。
     */
    @Override
    public List<Map<String, Object>> generateChannelStatement(String batchNo, double errorRate) {
        List<CrossBorderRemittance> localItems = reconMapper.selectSettledByBatch(batchNo, RemittanceStatus.SETTLED);
        List<Map<String, Object>> statement = new ArrayList<>();
        for (CrossBorderRemittance local : localItems) {
            if (errorRate > 0 && ThreadLocalRandom.current().nextDouble() < errorRate) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("remittanceNo", local.getRemittanceNo());
            BigDecimal amount = local.getTargetAmount();
            if (errorRate > 0 && ThreadLocalRandom.current().nextDouble() < errorRate) {
                amount = amount.multiply(new BigDecimal("1.001")).setScale(2, RoundingMode.HALF_UP);
            }
            item.put("amount", amount.toPlainString());
            item.put("currency", local.getTargetCurrency());
            item.put("settledAt", LocalDateTime.now().toString());
            statement.add(item);
        }
        if (errorRate > 0 && !localItems.isEmpty()) {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("remittanceNo", "CH-EXTRA-" + ThreadLocalRandom.current().nextInt(10000, 99999));
            extra.put("amount", new BigDecimal("100.00").toPlainString());
            extra.put("currency", "USD");
            extra.put("settledAt", LocalDateTime.now().toString());
            statement.add(extra);
        }
        return statement;
    }

    @Override
    public ReconReportResponse report(String batchNo) {
        SettlementBatch batch = batchMapper.selectByBatchNo(batchNo);
        if (batch == null) {
            throw new IllegalArgumentException("batch " + batchNo + " not found");
        }
        List<CrossBorderRemittance> localItems = reconMapper.selectSettledByBatch(batchNo, RemittanceStatus.SETTLED);
        List<ReconDiff> diffs = reconDiffMapper.selectByBatchNo(batchNo);
        int matched = localItems.size() - (int) diffs.stream()
                .filter(d -> d.getDiffType() == ReconDiffType.MISSING_IN_CHANNEL
                        || d.getDiffType() == ReconDiffType.AMOUNT_MISMATCH)
                .count();
        return buildReport(batch, localItems, List.of(), matched, diffs);
    }

    /**
     * 处理单笔差异。不同类型的差异处理方式不同，
     * 这里只标记处理状态与决策记录，真正的资金动作（退款、挂账）在真实系统里会走独立的财务流程。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> handleDiff(Long diffId, ReconDiffType diffType, String decision) {
        int updated = reconDiffMapper.markOneHandled(diffId);
        if (updated <= 0) {
            throw new IllegalStateException("diff " + diffId + " already handled or not found");
        }
        log.info("diff handled id={} type={} decision={}", diffId, diffType, decision);
        return Map.of("diffId", diffId, "diffType", diffType, "decision", decision, "status", "HANDLED");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int handleAllUnhandled(String batchNo, String decision) {
        int count = reconDiffMapper.markHandled(batchNo);
        log.info("batch diffs handled batchNo={} count={} decision={}", batchNo, count, decision);
        return count;
    }

    @Override
    public Map<String, Object> overview() {
        long unhandled = reconDiffMapper.countUnhandled();
        List<ReconDiff> recent = reconDiffMapper.selectAll(200);
        Map<String, Long> byType = new LinkedHashMap<>();
        for (ReconDiffType type : ReconDiffType.values()) {
            byType.put(type.name(), 0L);
        }
        for (ReconDiff diff : recent) {
            byType.merge(diff.getDiffType().name(), 1L, Long::sum);
        }
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("unhandledCount", unhandled);
        overview.put("totalRecent", recent.size());
        overview.put("byType", byType);
        return overview;
    }

    private ReconDiff buildDiff(String batchNo, String remittanceNo, ReconDiffType type,
                                BigDecimal localAmount, BigDecimal channelAmount) {
        ReconDiff diff = new ReconDiff();
        diff.setBatchNo(batchNo);
        diff.setRemittanceNo(remittanceNo);
        diff.setDiffType(type);
        diff.setLocalAmount(localAmount);
        diff.setChannelAmount(channelAmount);
        diff.setHandleStatus(0);
        return diff;
    }

    private ReconReportResponse buildReport(SettlementBatch batch, List<CrossBorderRemittance> localItems,
                                            List<Map<String, Object>> channelStatement,
                                            int matched, List<ReconDiff> diffs) {
        ReconReportResponse report = new ReconReportResponse();
        report.setBatchNo(batch.getBatchNo());
        report.setChannel(batch.getChannel());
        report.setCurrency(batch.getCurrency());
        report.setReconTime(LocalDateTime.now());
        report.setLocalCount(localItems.size());
        report.setLocalTotal(localItems.stream().map(CrossBorderRemittance::getTargetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        report.setChannelCount(channelStatement.size());
        report.setChannelTotal(channelStatement.isEmpty() ? BigDecimal.ZERO
                : channelStatement.stream().map(i -> new BigDecimal(String.valueOf(i.get("amount"))))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        report.setMatchedCount(matched);
        report.setDiffCount(diffs.size());
        report.setUnhandledCount((int) diffs.stream().filter(d -> d.getHandleStatus() == 0).count());
        report.setBalanced(diffs.isEmpty());
        Map<String, Object> byType = new LinkedHashMap<>();
        for (ReconDiffType type : ReconDiffType.values()) {
            List<ReconDiff> of = diffs.stream().filter(d -> d.getDiffType() == type).toList();
            byType.put(type.name(), Map.of("count", of.size(),
                    "totalLocal", of.stream().map(ReconDiff::getLocalAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                    "totalChannel", of.stream().map(ReconDiff::getChannelAmount).reduce(BigDecimal.ZERO, BigDecimal::add)));
        }
        report.setDiffByType(byType);
        report.setDiffs(diffs.stream().map(ReconDiffResponse::from).toList());
        return report;
    }

}
