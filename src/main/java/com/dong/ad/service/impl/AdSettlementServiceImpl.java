package com.dong.ad.service.impl;

import com.dong.ad.dto.AdChargeCommand;
import com.dong.ad.dto.DailyReportQuery;
import com.dong.ad.dto.DailyReportResponse;
import com.dong.ad.dto.ReconcileResponse;
import com.dong.ad.entity.AdAdvertiser;
import com.dong.ad.entity.AdCampaign;
import com.dong.ad.entity.AdChargeRecord;
import com.dong.ad.entity.AdDailyStat;
import com.dong.ad.entity.AdEvent;
import com.dong.ad.enums.AdChargeMode;
import com.dong.ad.enums.AdChargeStatus;
import com.dong.ad.enums.AdEventType;
import com.dong.ad.enums.AdSwitchStatus;
import com.dong.ad.mapper.AdAdvertiserMapper;
import com.dong.ad.mapper.AdCampaignMapper;
import com.dong.ad.mapper.AdChargeRecordMapper;
import com.dong.ad.mapper.AdDailyStatMapper;
import com.dong.ad.mapper.AdEventMapper;
import com.dong.ad.service.AdBudgetService;
import com.dong.ad.service.AdSettlementService;
import com.dong.ad.support.AdRuntimeStats;
import com.dong.ad.support.AdTopics;
import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.common.result.PageRequest;
import com.dong.common.result.PageResult;
import com.dong.common.util.JsonUtils;
import com.dong.framework.mq.MessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 计费结算与对账实现。
 *
 * <p>三条金额护栏按「失败代价从小到大」排序：总预算、账户余额、日预算。
 * 预检拦掉绝大多数拒绝，条件更新才是最终裁定；任一条件更新在并发下失败时，
 * 立刻反向补偿已成功的那几步，再写一条拒绝流水留痕。
 *
 * <p>所有有效事件都写流水，包括不扣钱的效果事件（金额为 0）。
 * 否则对账重算时会把「CPC 计划的曝光」当成不存在，CTR 直接失真。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdSettlementServiceImpl implements AdSettlementService {

    /**
     * adChargeRecordMapper，MyBatis Mapper 数据访问层。
     */
    private final AdChargeRecordMapper adChargeRecordMapper;

    /**
     * adDailyStatMapper，MyBatis Mapper 数据访问层。
     */
    private final AdDailyStatMapper adDailyStatMapper;

    /**
     * adCampaignMapper，MyBatis Mapper 数据访问层。
     */
    private final AdCampaignMapper adCampaignMapper;

    /**
     * adAdvertiserMapper，MyBatis Mapper 数据访问层。
     */
    private final AdAdvertiserMapper adAdvertiserMapper;

    /**
     * adEventMapper，MyBatis Mapper 数据访问层。
     */
    private final AdEventMapper adEventMapper;

    /**
     * adBudgetService，业务服务层。
     */
    private final AdBudgetService adBudgetService;

    /**
     * runtimeStats，链路计数。
     */
    private final AdRuntimeStats runtimeStats;

    /**
     * 消息生产者，补偿投递使用。
     */
    private final MessageProducer messageProducer;

    /**
     * 待计费事件超过多少分钟算消息丢失。
     */
    @Value("${dong.ad.charge-pending-minutes:5}")
    private int chargePendingMinutes;

    /**
     * 单轮最多补投多少条。
     */
    @Value("${dong.ad.charge-resend-limit:100}")
    private int chargeResendLimit;

    /**
     * 处理一条计费命令。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean charge(AdChargeCommand command) {
        if (adChargeRecordMapper.countByEventId(command.eventId()) > 0L) {
            log.info("duplicate ad charge dropped eventId={}", command.eventId());
            return false;
        }
        LocalDate statDate = parseDate(command.statDate());
        long amount = command.amountCents() == null ? 0L : Math.max(0L, command.amountCents());
        AdCampaign campaign = adCampaignMapper.selectById(command.campaignId());
        if (campaign == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "campaign " + command.campaignId() + " not found");
        }
        long impressions = command.eventType() == AdEventType.IMPRESSION.getCode() ? 1L : 0L;
        long clicks = command.eventType() == AdEventType.CLICK.getCode() ? 1L : 0L;
        adDailyStatMapper.insertIfAbsent(statDate, command.campaignId(), campaign.getDailyBudgetCents());
        if (amount == 0L) {
            insertRecord(command, statDate, 0L, AdChargeStatus.CHARGED, "");
            adDailyStatMapper.accumulateEffect(statDate, command.campaignId(), impressions, clicks);
            return true;
        }
        AdDailyStat stat = adDailyStatMapper.selectByDateAndCampaign(statDate, command.campaignId());
        AdAdvertiser advertiser = adAdvertiserMapper.selectById(command.advertiserId());
        if (stat == null || advertiser == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "campaign stat or advertiser not found");
        }
        String reason = precheck(campaign, stat, advertiser, amount);
        if (reason != null) {
            reject(command, statDate, amount, impressions, clicks, reason);
            return true;
        }
        // 总预算先扣：这一步失败没有任何副作用，后面的补偿链最短
        if (adCampaignMapper.addSpent(command.campaignId(), amount) == 0) {
            reject(command, statDate, amount, impressions, clicks, "total budget exhausted");
            return true;
        }
        if (adAdvertiserMapper.deductBalance(command.advertiserId(), amount, AdSwitchStatus.ON.getCode()) == 0) {
            adCampaignMapper.subtractSpent(command.campaignId(), amount);
            reject(command, statDate, amount, impressions, clicks, "no balance");
            return true;
        }
        if (adDailyStatMapper.accumulateCharged(statDate, command.campaignId(), amount, impressions, clicks) == 0) {
            adAdvertiserMapper.addBalance(command.advertiserId(), amount, AdSwitchStatus.ON.getCode());
            adCampaignMapper.subtractSpent(command.campaignId(), amount);
            reject(command, statDate, amount, impressions, clicks, "daily budget exhausted");
            return true;
        }
        insertRecord(command, statDate, amount, AdChargeStatus.CHARGED, "");
        markEvent(command.eventId(), AdChargeStatus.CHARGED, "charged");
        log.info("ad charge settled eventId={} campaign={} amount={}", command.eventId(), command.campaignId(), amount);
        return true;
    }

    /**
     * 以流水为权威源重算某天的日汇总，并重建 Redis 副本、补投漏单。
     */
    @Override
    public ReconcileResponse reconcile(LocalDate statDate, boolean resendPending) {
        long started = System.currentTimeMillis();
        LocalDate date = statDate == null ? LocalDate.now() : statDate;
        long count = adChargeRecordMapper.countByStatDate(date);
        if (count > Constants.MAX_BATCH_SIZE) {
            // 截断会把没查到的流水当成多余数据，得出「金额少了」的错误结论并覆盖正确汇总
            throw new BusinessException(Constants.CODE_CAPACITY_EXCEEDED, "charge records exceed safe capacity for " + date);
        }
        List<AdChargeRecord> records = adChargeRecordMapper.selectByStatDate(date, Constants.MAX_BATCH_SIZE);
        Map<Long, AdDailyStat> aggregates = new LinkedHashMap<>();
        for (AdChargeRecord record : records) {
            AdDailyStat stat = aggregates.computeIfAbsent(record.getCampaignId(), id -> newStat(date, id));
            if (record.getEventType() == AdEventType.IMPRESSION.getCode()) {
                stat.setImpressions(stat.getImpressions() + 1L);
            }
            if (record.getEventType() == AdEventType.CLICK.getCode()) {
                stat.setClicks(stat.getClicks() + 1L);
            }
            if (record.getStatus() == AdChargeStatus.CHARGED.getCode()) {
                stat.setChargeCents(stat.getChargeCents() + record.getAmountCents());
            } else if (record.getStatus() == AdChargeStatus.REJECTED.getCode()) {
                stat.setRejectedCents(stat.getRejectedCents() + record.getAmountCents());
                stat.setRejectedCount(stat.getRejectedCount() + 1L);
            }
        }
        ReconcileResponse response = new ReconcileResponse();
        response.setStatDate(date);
        response.setChargeRecords(records.size());
        response.setStats(aggregates.size());
        List<String> details = new ArrayList<>();
        int corrected = 0;
        int rebuilt = 0;
        long overspend = 0L;
        for (Map.Entry<Long, AdDailyStat> entry : aggregates.entrySet()) {
            Long campaignId = entry.getKey();
            AdDailyStat expected = entry.getValue();
            AdCampaign campaign = adCampaignMapper.selectById(campaignId);
            long budget = campaign == null ? 0L : campaign.getDailyBudgetCents();
            adDailyStatMapper.insertIfAbsent(date, campaignId, budget);
            AdDailyStat current = adDailyStatMapper.selectByDateAndCampaign(date, campaignId);
            if (current != null && differs(current, expected)) {
                adDailyStatMapper.overwrite(expected);
                corrected++;
                details.add("campaign " + campaignId + " stat corrected: charge " + current.getChargeCents() + " -> " + expected.getChargeCents());
            }
            if (campaign != null) {
                long total = adChargeRecordMapper.sumByCampaign(campaignId);
                if (total != campaign.getSpentTotalCents()) {
                    adCampaignMapper.overwriteSpent(campaignId, total);
                    details.add("campaign " + campaignId + " spent total corrected -> " + total);
                }
                try {
                    adBudgetService.rebuild(campaignId, date, budget, expected.getChargeCents());
                    rebuilt++;
                } catch (Exception ex) {
                    log.warn("rebuild ad budget replica failed campaign={}", campaignId, ex);
                }
            }
            if (expected.getChargeCents() > budget) {
                long gap = expected.getChargeCents() - budget;
                overspend += gap;
                details.add("campaign " + campaignId + " overspend " + gap + " cents");
            }
        }
        if (resendPending) {
            response.setResentEvents(resendPending(date));
        }
        response.setCorrectedStats(corrected);
        response.setRebuiltBudgets(rebuilt);
        response.setOverspendCents(overspend);
        response.setDetails(List.copyOf(details));
        response.setElapsedMillis(System.currentTimeMillis() - started);
        if (corrected > 0 || overspend > 0L) {
            log.warn("ad reconcile date={} corrected={} overspend={}", date, corrected, overspend);
        }
        return response;
    }

    /**
     * 分页查询日报表。
     */
    @Override
    public PageResult<DailyReportResponse> dailyReport(DailyReportQuery query) {
        LocalDate today = LocalDate.now();
        LocalDate from = query.getDateFrom() == null ? today.minusDays(6L) : query.getDateFrom();
        LocalDate to = query.getDateTo() == null ? today : query.getDateTo();
        if (from.isAfter(to)) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "dateFrom is after dateTo");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1L > Constants.MAX_REPORT_DAYS) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "date range exceeds " + Constants.MAX_REPORT_DAYS + " days");
        }
        PageRequest page = query.toPageRequest();
        List<AdDailyStat> list = adDailyStatMapper.selectByPage(from, to, query.getCampaignId(), page.getOffset(), page.getPageSize());
        long total = adDailyStatMapper.countByPage(from, to, query.getCampaignId());
        return PageResult.of(list, total, page).map(DailyReportResponse::from);
    }

    /**
     * 查询计划今日已计费金额。
     */
    @Override
    public long todayCharge(Long campaignId) {
        AdDailyStat stat = adDailyStatMapper.selectByDateAndCampaign(LocalDate.now(), campaignId);
        return stat == null ? 0L : stat.getChargeCents();
    }

    /**
     * 三道护栏的预检。真正的裁定仍是后面的条件更新，
     * 预检只是为了把绝大多数拒绝挡在任何写操作之前。
     *
     * @param campaign   计划
     * @param stat       当日汇总
     * @param advertiser 广告主
     * @param amount     本笔金额，单位分
     * @return 拒绝原因，null 表示通过
     */
    private String precheck(AdCampaign campaign, AdDailyStat stat, AdAdvertiser advertiser, long amount) {
        if (stat.getChargeCents() + amount > stat.getBudgetCents()) {
            return "daily budget exhausted";
        }
        if (campaign.getSpentTotalCents() + amount > campaign.getTotalBudgetCents()) {
            return "total budget exhausted";
        }
        if (advertiser.getStatus() != AdSwitchStatus.ON.getCode() || advertiser.getBalanceCents() < amount) {
            return "no balance";
        }
        return null;
    }

    /**
     * 写一条被拒绝的流水并反向归还 Redis 预扣。
     *
     * @param command     计费命令
     * @param statDate    归属日期
     * @param amount      被挡下的金额，单位分
     * @param impressions 曝光增量
     * @param clicks      点击增量
     * @param reason      拒绝原因
     */
    private void reject(AdChargeCommand command, LocalDate statDate, long amount, long impressions, long clicks, String reason) {
        insertRecord(command, statDate, amount, AdChargeStatus.REJECTED, reason);
        adDailyStatMapper.accumulateRejected(statDate, command.campaignId(), amount, impressions, clicks);
        markEvent(command.eventId(), AdChargeStatus.REJECTED, reason);
        runtimeStats.getRejected().increment();
        if (AdChargeMode.REDIS.text().equals(command.chargeMode())) {
            try {
                adBudgetService.restore(command.campaignId(), statDate, amount);
            } catch (Exception ex) {
                // 副本归还失败不致命：对账任务会以流水为准重建它
                log.warn("restore ad budget replica failed eventId={}", command.eventId(), ex);
            }
        }
        log.info("ad charge rejected eventId={} reason={} amount={}", command.eventId(), reason, amount);
    }

    /**
     * 补投超时待计费事件。只发消息，不在这里同步计费：
     * 同类内部调用 charge 会绕过 Spring 事务代理，反而破坏一致性。
     *
     * @param date 对账日期
     * @return 补投条数
     */
    private int resendPending(LocalDate date) {
        LocalDateTime before = LocalDateTime.now().minusMinutes(Math.max(1, chargePendingMinutes));
        int limit = Math.min(Math.max(1, chargeResendLimit), Constants.MAX_BATCH_SIZE);
        List<AdEvent> pending = adEventMapper.selectPending(AdChargeStatus.PENDING.getCode(), before, limit);
        int resent = 0;
        for (AdEvent event : pending) {
            try {
                LocalDate eventDate = event.getCreateTime() == null ? date : event.getCreateTime().toLocalDate();
                AdChargeCommand command = new AdChargeCommand(event.getEventId(), event.getTraceId(), eventDate.toString(),
                        event.getCampaignId(), event.getUnitId(), event.getCreativeId(), event.getAdvertiserId(),
                        event.getEventType(), 0, event.getClearingCents(), event.getChargeMode());
                messageProducer.send(AdTopics.CHARGE, event.getEventId(), JsonUtils.toJson(command));
                resent++;
            } catch (Exception ex) {
                log.error("resend ad charge failed eventId={}", event.getEventId(), ex);
            }
        }
        if (resent > 0) {
            log.warn("ad charge resent {} pending events, charge dedup relies on uk_charge_event", resent);
        }
        return resent;
    }

    /**
     * 插入一条计费流水。
     *
     * @param command  计费命令
     * @param statDate 归属日期
     * @param amount   金额，单位分
     * @param status   流水状态
     * @param reason   说明
     */
    private void insertRecord(AdChargeCommand command, LocalDate statDate, long amount, AdChargeStatus status, String reason) {
        AdChargeRecord record = new AdChargeRecord();
        record.setEventId(command.eventId());
        record.setTraceId(command.traceId());
        record.setStatDate(statDate);
        record.setCampaignId(command.campaignId());
        record.setUnitId(command.unitId());
        record.setCreativeId(command.creativeId());
        record.setAdvertiserId(command.advertiserId());
        record.setEventType(command.eventType());
        record.setAmountCents(amount);
        record.setStatus(status.getCode());
        record.setChargeMode(command.chargeMode());
        record.setRejectReason(reason);
        adChargeRecordMapper.insert(record);
    }

    /**
     * 回写事件的计费处置结论。
     *
     * @param eventId 事件幂等键
     * @param status  计费状态
     * @param reason  说明
     */
    private void markEvent(String eventId, AdChargeStatus status, String reason) {
        AdEvent event = adEventMapper.selectByEventId(eventId);
        if (event != null) {
            adEventMapper.updateChargeStatus(event.getId(), status.getCode(), reason);
        }
    }

    /**
     * 构造零值汇总行。
     *
     * @param date       统计日期
     * @param campaignId 计划 id
     * @return 零值汇总行
     */
    private AdDailyStat newStat(LocalDate date, Long campaignId) {
        AdDailyStat stat = new AdDailyStat();
        stat.setStatDate(date);
        stat.setCampaignId(campaignId);
        stat.setBudgetCents(0L);
        stat.setImpressions(0L);
        stat.setClicks(0L);
        stat.setChargeCents(0L);
        stat.setRejectedCents(0L);
        stat.setRejectedCount(0L);
        return stat;
    }

    /**
     * 比较实时汇总与流水重算结果是否一致。
     *
     * @param current  库中汇总
     * @param expected 流水重算结果
     * @return 是否有差异
     */
    private boolean differs(AdDailyStat current, AdDailyStat expected) {
        return !Objects.equals(current.getImpressions(), expected.getImpressions())
                || !Objects.equals(current.getClicks(), expected.getClicks())
                || !Objects.equals(current.getChargeCents(), expected.getChargeCents())
                || !Objects.equals(current.getRejectedCents(), expected.getRejectedCents())
                || !Objects.equals(current.getRejectedCount(), expected.getRejectedCount());
    }

    /**
     * 解析归属日期。畸形消息在 handler 已过滤，这里再兜一次避免脏数据落到默认日期。
     *
     * @param text 日期字符串
     * @return 归属日期
     */
    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text);
        } catch (Exception ex) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "invalid stat date " + text);
        }
    }

}
