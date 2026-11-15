package com.dong.ad.service.impl;

import com.dong.ad.dto.AdChargeCommand;
import com.dong.ad.dto.AdEventRequest;
import com.dong.ad.dto.AdEventResponse;
import com.dong.ad.dto.AdQuery;
import com.dong.ad.dto.DeliveryRequest;
import com.dong.ad.dto.DeliveryResponse;
import com.dong.ad.entity.AdAdvertiser;
import com.dong.ad.entity.AdCampaign;
import com.dong.ad.entity.AdCreative;
import com.dong.ad.entity.AdDailyStat;
import com.dong.ad.entity.AdEvent;
import com.dong.ad.entity.AdUnit;
import com.dong.ad.enums.AdBidType;
import com.dong.ad.enums.AdCampaignStatus;
import com.dong.ad.enums.AdChargeMode;
import com.dong.ad.enums.AdChargeStatus;
import com.dong.ad.enums.AdCreativeStatus;
import com.dong.ad.enums.AdEventType;
import com.dong.ad.enums.AdSwitchStatus;
import com.dong.ad.mapper.AdAdvertiserMapper;
import com.dong.ad.mapper.AdCampaignMapper;
import com.dong.ad.mapper.AdCreativeMapper;
import com.dong.ad.mapper.AdDailyStatMapper;
import com.dong.ad.mapper.AdEventMapper;
import com.dong.ad.mapper.AdUnitMapper;
import com.dong.ad.service.AdBudgetService;
import com.dong.ad.service.AdDeliveryService;
import com.dong.ad.service.AdSettlementService;
import com.dong.ad.support.AdPricingSupport;
import com.dong.ad.support.AdRuntimeStats;
import com.dong.ad.support.AdTargetingSupport;
import com.dong.ad.support.AdTopics;
import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.common.result.PageRequest;
import com.dong.common.result.PageResult;
import com.dong.common.util.JsonUtils;
import com.dong.common.util.Snowflake;
import com.dong.framework.mq.MessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 投放链路实现：召回过滤、定向匹配、广义二价竞价、令牌签发、事件上报。
 *
 * <p>信任边界在令牌上：召回时由服务端算好成交价并落库，曝光与点击上报
 * 只带 traceId 与幂等键，不接受任何价格、计划或用户信息。
 * 否则客户端把自己点成一分钱只需改一个 JSON 字段。
 *
 * <p>「Redis 预扣」只是加速闸门，不是裁定者。闸门判定配额不足时不直接拒绝，
 * 而是切回数据库条件扣减，由权威流水做最终裁定——副本可能滞后于库，
 * 拿副本当真相会冤枉本来还能投的一笔。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdDeliveryServiceImpl implements AdDeliveryService {

    private static final String TRACE_PREFIX = "AD";

    private static final String TOKEN_SUFFIX = ":REQ";

    /**
     * adCampaignMapper，MyBatis Mapper 数据访问层。
     */
    private final AdCampaignMapper adCampaignMapper;

    /**
     * adUnitMapper，MyBatis Mapper 数据访问层。
     */
    private final AdUnitMapper adUnitMapper;

    /**
     * adCreativeMapper，MyBatis Mapper 数据访问层。
     */
    private final AdCreativeMapper adCreativeMapper;

    /**
     * adAdvertiserMapper，MyBatis Mapper 数据访问层。
     */
    private final AdAdvertiserMapper adAdvertiserMapper;

    /**
     * adDailyStatMapper，MyBatis Mapper 数据访问层。
     */
    private final AdDailyStatMapper adDailyStatMapper;

    /**
     * adEventMapper，MyBatis Mapper 数据访问层。
     */
    private final AdEventMapper adEventMapper;

    /**
     * adSettlementService，业务服务层。
     */
    private final AdSettlementService adSettlementService;

    /**
     * adBudgetService，业务服务层。
     */
    private final AdBudgetService adBudgetService;

    /**
     * targetingSupport，定向条件工具。
     */
    private final AdTargetingSupport targetingSupport;

    /**
     * pricingSupport，竞价与计费计算。
     */
    private final AdPricingSupport pricingSupport;

    /**
     * runtimeStats，链路计数。
     */
    private final AdRuntimeStats runtimeStats;

    /**
     * 消息生产者，计费异步化使用。
     */
    private final MessageProducer messageProducer;

    /**
     * 雪花 ID 生成器。
     */
    private final Snowflake snowflake;

    /**
     * Redis 预算闸门开关。
     */
    @Value("${dong.ad.redis-budget-enabled:true}")
    private boolean redisBudgetEnabled;

    /**
     * 计费消息开关。
     */
    @Value("${dong.ad.mq-charge-enabled:true}")
    private boolean mqChargeEnabled;

    /**
     * 频次控制开关。
     */
    @Value("${dong.ad.frequency-cap-enabled:true}")
    private boolean frequencyCapEnabled;

    /**
     * 单用户单计划每日曝光上限。
     */
    @Value("${dong.ad.frequency-cap-per-day:3}")
    private int frequencyCapPerDay;

    /**
     * 令牌有效期，单位分钟。
     */
    @Value("${dong.ad.trace-ttl-minutes:30}")
    private int traceTtlMinutes;

    /**
     * 点击距曝光的最小间隔，单位毫秒。
     */
    @Value("${dong.ad.min-click-delay-ms:200}")
    private long minClickDelayMs;

    /**
     * 点击归因窗口，单位秒。
     */
    @Value("${dong.ad.click-window-seconds:1800}")
    private long clickWindowSeconds;

    /**
     * UA 黑名单关键字，逗号分隔。
     */
    @Value("${dong.ad.blocked-ua-keywords:bot,spider,crawler}")
    private String blockedUaKeywords;

    /**
     * IP 黑名单前缀，逗号分隔。
     */
    @Value("${dong.ad.blocked-ip-prefixes:}")
    private String blockedIpPrefixes;

    /**
     * CPM 千次价格折算除数。
     */
    @Value("${dong.ad.cpm-divisor:1000}")
    private int cpmDivisor;

    /**
     * 处理一次广告位请求。
     */
    @Override
    public DeliveryResponse request(DeliveryRequest request) {
        runtimeStats.getRequests().increment();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        if (isBlocked(request)) {
            diagnostics.put("blockedTraffic", true);
            runtimeStats.getFiltered().increment();
            return DeliveryResponse.empty("blocked traffic", diagnostics);
        }
        List<AdCampaign> campaigns = adCampaignMapper.selectDeliverable(AdCampaignStatus.DELIVERING.getCode(), today, Constants.MAX_QUERY_LIMIT);
        diagnostics.put("campaignsInWindow", campaigns.size());
        Map<Long, AdAdvertiser> advertisers = new LinkedHashMap<>();
        List<AdCampaign> eligible = new ArrayList<>();
        int hoursFiltered = 0;
        int balanceFiltered = 0;
        int budgetFiltered = 0;
        int frequencyFiltered = 0;
        for (AdCampaign campaign : campaigns) {
            if (!hourAllowed(campaign.getDeliveryHours(), hour)) {
                hoursFiltered++;
                continue;
            }
            AdAdvertiser advertiser = advertisers.computeIfAbsent(campaign.getAdvertiserId(), adAdvertiserMapper::selectById);
            if (advertiser == null || advertiser.getStatus() != AdSwitchStatus.ON.getCode() || advertiser.getBalanceCents() <= 0L) {
                balanceFiltered++;
                continue;
            }
            AdDailyStat stat = adDailyStatMapper.selectByDateAndCampaign(today, campaign.getId());
            long todayCharge = stat == null ? 0L : stat.getChargeCents();
            if (todayCharge >= campaign.getDailyBudgetCents() || campaign.getSpentTotalCents() >= campaign.getTotalBudgetCents()) {
                budgetFiltered++;
                continue;
            }
            if (frequencyCapEnabled && overFrequency(campaign.getId(), request.getUserId(), today)) {
                frequencyFiltered++;
                continue;
            }
            eligible.add(campaign);
        }
        diagnostics.put("hoursFiltered", hoursFiltered);
        diagnostics.put("balanceFiltered", balanceFiltered);
        diagnostics.put("budgetFiltered", budgetFiltered);
        diagnostics.put("frequencyFiltered", frequencyFiltered);
        diagnostics.put("eligibleCampaigns", eligible.size());
        if (eligible.isEmpty()) {
            return DeliveryResponse.empty("no eligible campaign", diagnostics);
        }
        List<Long> campaignIds = eligible.stream().map(AdCampaign::getId).toList();
        List<AdUnit> units = adUnitMapper.selectByCampaignIds(campaignIds, AdSwitchStatus.ON.getCode(), Constants.MAX_QUERY_LIMIT);
        diagnostics.put("enabledUnits", units.size());
        if (units.isEmpty()) {
            return DeliveryResponse.empty("no enabled unit", diagnostics);
        }
        List<Long> unitIds = units.stream().map(AdUnit::getId).toList();
        List<AdCreative> creatives = adCreativeMapper.selectApprovedByUnitIds(unitIds, AdCreativeStatus.APPROVED.getCode(), Constants.MAX_QUERY_LIMIT);
        Map<Long, AdCreative> creativeByUnit = new LinkedHashMap<>();
        for (AdCreative creative : creatives) {
            creativeByUnit.putIfAbsent(creative.getUnitId(), creative);
        }
        Map<Long, AdCampaign> campaignById = new LinkedHashMap<>();
        for (AdCampaign campaign : eligible) {
            campaignById.put(campaign.getId(), campaign);
        }
        List<Ranked> ranked = new ArrayList<>();
        int targetingFiltered = 0;
        for (AdUnit unit : units) {
            AdCreative creative = creativeByUnit.get(unit.getId());
            if (creative == null || !campaignById.containsKey(unit.getCampaignId())) {
                continue;
            }
            boolean matched;
            try {
                matched = targetingSupport.matches(unit.getTargeting(), request);
            } catch (BusinessException ex) {
                // 一条脏定向只刷掉自己，不能让整次广告请求失败
                log.warn("invalid ad targeting skipped unit={}", unit.getId(), ex);
                continue;
            }
            if (!matched) {
                targetingFiltered++;
                continue;
            }
            ranked.add(new Ranked(campaignById.get(unit.getCampaignId()), unit, creative,
                    pricingSupport.score(unit.getBidCents(), unit.getPctr())));
        }
        ranked.sort(Comparator.comparingLong(Ranked::score).reversed());
        diagnostics.put("targetingFiltered", targetingFiltered);
        diagnostics.put("rankedCandidates", ranked.size());
        if (ranked.isEmpty()) {
            return DeliveryResponse.empty("no creative matched targeting", diagnostics);
        }
        Ranked winner = ranked.get(0);
        long secondScore = ranked.size() > 1 ? ranked.get(1).score() : 0L;
        long clearing = pricingSupport.clearing(winner.unit().getBidCents(), secondScore, winner.unit().getPctr(), winner.unit().getFloorCents());
        AdBidType bidType = AdBidType.of(winner.unit().getBidType());
        long charge = pricingSupport.charge(clearing, bidType, cpmDivisor);
        diagnostics.put("secondPriceApplied", secondScore > 0L);
        String traceId = TRACE_PREFIX + snowflake.nextId();
        LocalDateTime expireTime = now.plusMinutes(Math.max(1, traceTtlMinutes));
        insertToken(traceId, winner, request, charge, expireTime);
        List<DeliveryResponse.Candidate> candidates = ranked.stream().limit(request.getMaxCandidates())
                .map(item -> new DeliveryResponse.Candidate(item.campaign().getId(), item.unit().getId(),
                        item.creative().getId(), item.creative().getTitle(),
                        AdBidType.of(item.unit().getBidType()).name(), item.unit().getBidCents(),
                        item.unit().getPctr(), item.score(), item == winner ? charge : 0L))
                .toList();
        DeliveryResponse response = new DeliveryResponse();
        response.setMatched(true);
        response.setTraceId(traceId);
        response.setCampaignId(winner.campaign().getId());
        response.setUnitId(winner.unit().getId());
        response.setCreativeId(winner.creative().getId());
        response.setTitle(winner.creative().getTitle());
        response.setImageUrl(winner.creative().getImageUrl());
        response.setLandingUrl(winner.creative().getLandingUrl());
        response.setBidType(bidType.name());
        response.setBidCents(winner.unit().getBidCents());
        response.setClearingCents(charge);
        response.setScore(winner.score());
        response.setCandidateCount(ranked.size());
        response.setCandidates(candidates);
        response.setExpireTime(expireTime);
        response.setDiagnostics(diagnostics);
        runtimeStats.getMatched().increment();
        log.info("ad delivered trace={} campaign={} unit={} clearing={} candidates={}", traceId,
                winner.campaign().getId(), winner.unit().getId(), charge, ranked.size());
        return response;
    }

    /**
     * 上报事件，按当前配置选择路径。
     */
    @Override
    public AdEventResponse track(AdEventRequest request, AdEventType type) {
        return track(request, type, null, false);
    }

    /**
     * 上报事件并显式指定闸门路径与同步计费，供对照实验使用。
     */
    @Override
    public AdEventResponse track(AdEventRequest request, AdEventType type, AdChargeMode explicitMode, boolean syncCharge) {
        AdEvent existing = adEventMapper.selectByEventId(request.getEventId());
        if (existing != null) {
            if (!existing.getTraceId().equals(request.getTraceId()) || existing.getEventType() != type.getCode()
                    || !Objects.equals(existing.getUserId(), request.getUserId())) {
                throw new BusinessException(Constants.CODE_IDEMPOTENT_REJECTED, "eventId reused for a different event");
            }
            runtimeStats.getDuplicates().increment();
            return AdEventResponse.replayed(request.getEventId(), request.getTraceId(), type);
        }
        AdEvent token = adEventMapper.selectByTraceAndType(request.getTraceId(), AdEventType.REQUEST.getCode());
        if (token == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "trace " + request.getTraceId() + " not found");
        }
        LocalDateTime now = LocalDateTime.now();
        String filterReason = validateEvent(token, request, type, now, syncCharge);
        if (filterReason == null) {
            filterReason = validateLive(token);
        }
        if (filterReason != null) {
            insertEvent(request, token, type, 0L, AdChargeStatus.NONE, 0, filterReason, AdChargeMode.DB.text(), null);
            runtimeStats.getFiltered().increment();
            return AdEventResponse.filtered(request.getEventId(), request.getTraceId(), type, filterReason);
        }
        AdUnit unit = adUnitMapper.selectById(token.getUnitId());
        AdCampaign campaign = adCampaignMapper.selectById(token.getCampaignId());
        if (unit == null || campaign == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "unit or campaign disappeared mid-flight");
        }
        AdBidType bidType = AdBidType.of(unit.getBidType());
        boolean chargeable = pricingSupport.chargeable(type, bidType);
        long amount = chargeable ? Math.max(0L, token.getClearingCents()) : 0L;
        LocalDate statDate = now.toLocalDate();
        AdChargeMode mode = explicitMode == null
                ? (redisBudgetEnabled && amount > 0L ? AdChargeMode.REDIS : AdChargeMode.DB)
                : explicitMode;
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        if (amount > 0L && mode == AdChargeMode.REDIS) {
            AdDailyStat stat = adDailyStatMapper.selectByDateAndCampaign(statDate, campaign.getId());
            long todayCharge = stat == null ? 0L : stat.getChargeCents();
            try {
                long left = adBudgetService.reserve(campaign.getId(), statDate, campaign.getDailyBudgetCents(), todayCharge, amount);
                if (left < 0L) {
                    // 副本认为配额不足也不直接拒：切回库，由权威流水裁定，避免副本滞后冤枉一笔
                    mode = AdChargeMode.DB;
                    diagnostics.put("redisGateExhausted", true);
                }
            } catch (Exception ex) {
                mode = AdChargeMode.DB;
                runtimeStats.getRedisFallbacks().increment();
                log.warn("ad redis budget gate degraded trace={}", request.getTraceId(), ex);
            }
        }
        String modeText = amount > 0L ? mode.text() : AdChargeMode.DB.text();
        AdChargeStatus pending = chargeable ? AdChargeStatus.PENDING : AdChargeStatus.NONE;
        AdEvent event = insertEvent(request, token, type, amount, pending, 1, "", modeText, null);
        if (event == null) {
            runtimeStats.getDuplicates().increment();
            return AdEventResponse.replayed(request.getEventId(), request.getTraceId(), type);
        }
        if (type == AdEventType.IMPRESSION && frequencyCapEnabled) {
            try {
                adBudgetService.incrementFrequency(campaign.getId(), request.getUserId(), statDate);
            } catch (Exception ex) {
                // 控频是体验优化，不是计费正确性前提，Redis 挂了就先不控
                log.warn("ad frequency increment degraded trace={}", request.getTraceId(), ex);
            }
        }
        AdChargeCommand command = new AdChargeCommand(event.getEventId(), event.getTraceId(), statDate.toString(),
                event.getCampaignId(), event.getUnitId(), event.getCreativeId(), event.getAdvertiserId(),
                type.getCode(), unit.getBidType(), amount, modeText);
        boolean async = mqChargeEnabled && !syncCharge && chargeable;
        if (async) {
            try {
                messageProducer.send(AdTopics.CHARGE, event.getEventId(), JsonUtils.toJson(command));
                diagnostics.put("chargePath", "mq");
            } catch (Exception ex) {
                runtimeStats.getMqFallbacks().increment();
                log.warn("ad charge mq degraded eventId={}", event.getEventId(), ex);
                safeCharge(command, diagnostics, "sync-fallback");
            }
        } else {
            safeCharge(command, diagnostics, "sync");
        }
        AdEventResponse response = AdEventResponse.accepted(request.getEventId(), request.getTraceId(), type,
                amount, modeText, pending);
        response.setDiagnostics(diagnostics);
        runtimeStats.getEvents().increment();
        return response;
    }

    /**
     * 为并发实验直接签发一条指定计划的令牌，跳过召回过滤。
     */
    @Override
    public String issueTokenForLab(Long campaignId, Long userId) {
        AdCampaign campaign = adCampaignMapper.selectById(campaignId);
        if (campaign == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "campaign " + campaignId + " not found");
        }
        List<AdUnit> units = adUnitMapper.selectByCampaign(campaignId, Constants.MAX_QUERY_LIMIT);
        AdUnit unit = units.stream().filter(item -> item.getStatus() == AdSwitchStatus.ON.getCode()).findFirst()
                .orElseThrow(() -> new BusinessException(Constants.CODE_OPERATION_CONFLICT, "no enabled unit in campaign"));
        List<AdCreative> creatives = adCreativeMapper.selectByUnit(unit.getId(), Constants.MAX_QUERY_LIMIT);
        AdCreative creative = creatives.stream().filter(item -> item.getStatus() == AdCreativeStatus.APPROVED.getCode()).findFirst()
                .orElseThrow(() -> new BusinessException(Constants.CODE_OPERATION_CONFLICT, "no approved creative in unit"));
        long clearing = pricingSupport.clearing(unit.getBidCents(), 0L, unit.getPctr(), unit.getFloorCents());
        long charge = pricingSupport.charge(clearing, AdBidType.of(unit.getBidType()), cpmDivisor);
        DeliveryRequest context = new DeliveryRequest();
        context.setUserId(userId);
        context.setRegion("lab");
        context.setDevice("lab");
        context.setIp("127.0.0.1");
        context.setUserAgent("lab-benchmark");
        Ranked ranked = new Ranked(campaign, unit, creative, pricingSupport.score(unit.getBidCents(), unit.getPctr()));
        String traceId = TRACE_PREFIX + snowflake.nextId();
        insertToken(traceId, ranked, context, charge, LocalDateTime.now().plusMinutes(Math.max(1, traceTtlMinutes)));
        return traceId;
    }

    /**
     * 分页查询投放事件。诊断用途，直接返回落库行以便查看原始 valid 与 chargeStatus。
     */
    @Override
    public PageResult<Object> events(AdQuery query) {
        Integer valid = null;
        if (query.getStatus() != null) {
            AdSwitchStatus.of(query.getStatus());
            valid = query.getStatus() == 1 ? 1 : 0;
        }
        PageRequest page = query.toPageRequest();
        List<AdEvent> list = adEventMapper.selectByPage(query.getParentId(), query.getTraceId(), valid, page.getOffset(), page.getPageSize());
        long total = adEventMapper.countByPage(query.getParentId(), query.getTraceId(), valid);
        return PageResult.of(list, total, page).map(item -> (Object) item);
    }

    /**
     * 查看投放链路运行时状态。
     */
    @Override
    public Map<String, Object> runtime() {
        Map<String, Object> runtime = new LinkedHashMap<>(runtimeStats.snapshot());
        runtime.put("redisBudgetEnabled", redisBudgetEnabled);
        runtime.put("mqChargeEnabled", mqChargeEnabled);
        runtime.put("frequencyCapEnabled", frequencyCapEnabled);
        runtime.put("frequencyCapPerDay", frequencyCapPerDay);
        runtime.put("traceTtlMinutes", traceTtlMinutes);
        runtime.put("cpmDivisor", cpmDivisor);
        return runtime;
    }

    /**
     * 同步计费的安全包装。计费失败不把异常抛回媒体侧：
     * 事件已落库且处于 PENDING，补偿任务会按同一幂等键重投，重试接口只会拿到重放响应。
     *
     * @param command     计费命令
     * @param diagnostics 链路诊断
     * @param path        计费路径名称
     */
    private void safeCharge(AdChargeCommand command, Map<String, Object> diagnostics, String path) {
        try {
            adSettlementService.charge(command);
            diagnostics.put("chargePath", path);
        } catch (Exception ex) {
            log.error("ad charge deferred eventId={}", command.eventId(), ex);
            diagnostics.put("chargePath", "deferred");
        }
    }

    /**
     * 校验事件本身是否可信。返回 null 表示通过，否则返回过滤原因。
     *
     * @param token          令牌行
     * @param request        上报请求
     * @param type           事件类型
     * @param now            当前时间
     * @param allowFastClick 实验模式豁免最小点击间隔，压测里曝光与点击几乎同时发生
     * @return 过滤原因
     */
    private String validateEvent(AdEvent token, AdEventRequest request, AdEventType type, LocalDateTime now, boolean allowFastClick) {
        if (token.getExpireTime() == null || now.isAfter(token.getExpireTime())) {
            return "expired trace";
        }
        if (!Objects.equals(token.getUserId(), request.getUserId())) {
            return "user mismatch";
        }
        if (adEventMapper.countByTraceAndType(request.getTraceId(), type.getCode()) > 0L) {
            return "duplicate event type";
        }
        if (isBlocked(request.getUserAgent(), request.getIp())) {
            return "blocked traffic";
        }
        if (type == AdEventType.CLICK) {
            AdEvent impression = adEventMapper.selectByTraceAndType(request.getTraceId(), AdEventType.IMPRESSION.getCode());
            if (impression == null) {
                return "click without impression";
            }
            long gapMillis = impression.getCreateTime() == null ? -1L : Duration.between(impression.getCreateTime(), now).toMillis();
            if (!allowFastClick && gapMillis >= 0L && gapMillis < minClickDelayMs) {
                return "too fast click";
            }
            if (gapMillis > clickWindowSeconds * 1000L) {
                return "click out of window";
            }
        }
        return null;
    }

    /**
     * 校验广告在事件到达时是否仍在投放。下线后的流量不计费，
     * 但事件照常留痕，便于查「为什么这批点击没结算」。
     *
     * @param token 令牌行
     * @return 过滤原因
     */
    private String validateLive(AdEvent token) {
        AdCampaign campaign = adCampaignMapper.selectById(token.getCampaignId());
        if (campaign == null || campaign.getStatus() != AdCampaignStatus.DELIVERING) {
            return "campaign not delivering";
        }
        AdUnit unit = adUnitMapper.selectById(token.getUnitId());
        if (unit == null || unit.getStatus() != AdSwitchStatus.ON.getCode()) {
            return "unit paused";
        }
        AdCreative creative = adCreativeMapper.selectById(token.getCreativeId());
        if (creative == null || creative.getStatus() != AdCreativeStatus.APPROVED.getCode()) {
            return "creative not approved";
        }
        return null;
    }

    /**
     * 插入令牌行。成交价、计划归属与过期时间全部由服务端决定。
     *
     * @param traceId    链路 id
     * @param winner     胜出候选
     * @param request    投放请求
     * @param charge     本笔成交金额，单位分
     * @param expireTime 过期时间
     */
    private void insertToken(String traceId, Ranked winner, DeliveryRequest request, long charge, LocalDateTime expireTime) {
        AdEvent token = new AdEvent();
        token.setTraceId(traceId);
        token.setEventId(traceId + TOKEN_SUFFIX);
        token.setEventType(AdEventType.REQUEST.getCode());
        token.setCampaignId(winner.campaign().getId());
        token.setUnitId(winner.unit().getId());
        token.setCreativeId(winner.creative().getId());
        token.setAdvertiserId(winner.campaign().getAdvertiserId());
        token.setUserId(request.getUserId());
        token.setRegion(text(request.getRegion()));
        token.setDevice(text(request.getDevice()));
        token.setIp(text(request.getIp()));
        token.setBidCents(winner.unit().getBidCents());
        token.setClearingCents(charge);
        token.setChargeStatus(AdChargeStatus.NONE.getCode());
        token.setValid(1);
        token.setRejectReason("");
        token.setChargeMode(AdChargeMode.DB.text());
        token.setExpireTime(expireTime);
        try {
            adEventMapper.insert(token);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "trace id collision");
        }
    }

    /**
     * 插入事件行。两条唯一键在这里兜住重放与换 key 刷量，冲突时返回 null。
     *
     * @param request     上报请求
     * @param token       令牌行
     * @param type        事件类型
     * @param chargeCents 本笔成交金额，单位分
     * @param status      计费状态
     * @param valid       是否有效
     * @param reason      处置说明
     * @param mode        预算闸门路径
     * @param expireTime  过期时间，事件行为 null
     * @return 落库事件，唯一键冲突时返回 null
     */
    private AdEvent insertEvent(AdEventRequest request, AdEvent token, AdEventType type, long chargeCents,
                                AdChargeStatus status, int valid, String reason, String mode, LocalDateTime expireTime) {
        AdEvent event = new AdEvent();
        event.setTraceId(token.getTraceId());
        event.setEventId(request.getEventId());
        event.setEventType(type.getCode());
        event.setCampaignId(token.getCampaignId());
        event.setUnitId(token.getUnitId());
        event.setCreativeId(token.getCreativeId());
        event.setAdvertiserId(token.getAdvertiserId());
        event.setUserId(request.getUserId());
        event.setRegion(token.getRegion());
        event.setDevice(token.getDevice());
        event.setIp(request.getIp() == null ? token.getIp() : text(request.getIp()));
        event.setBidCents(token.getBidCents());
        event.setClearingCents(chargeCents);
        event.setChargeStatus(status.getCode());
        event.setValid(valid);
        event.setRejectReason(reason);
        event.setChargeMode(mode);
        event.setExpireTime(expireTime);
        try {
            adEventMapper.insert(event);
        } catch (DuplicateKeyException ex) {
            return null;
        }
        return event;
    }

    /**
     * 判断当前小时是否允许投放。脏时段串放行并记日志，避免一条配置写错让全站零填充。
     *
     * @param hours 24 位时段串
     * @param hour  当前小时
     * @return 是否允许
     */
    private boolean hourAllowed(String hours, int hour) {
        if (hours == null || hours.length() != 24 || hour < 0 || hour > 23) {
            log.warn("invalid ad delivery hours={}", hours);
            return true;
        }
        return hours.charAt(hour) == '1';
    }

    /**
     * 频次预检。Redis 不可用直接降级为不控频，
     * 控频是体验优化，不该让一次缓存抖动变成广告位空窗。
     *
     * @param campaignId 计划 id
     * @param userId     用户 id
     * @param date       归属日期
     * @return 是否已超限
     */
    private boolean overFrequency(Long campaignId, Long userId, LocalDate date) {
        try {
            return adBudgetService.frequencyCount(campaignId, userId, date) >= Math.max(1, Math.min(frequencyCapPerDay, Constants.MAX_FREQUENCY_CAP));
        } catch (Exception ex) {
            runtimeStats.getRedisFallbacks().increment();
            log.warn("ad frequency check degraded campaign={} user={}", campaignId, userId, ex);
            return false;
        }
    }

    /**
     * 判断请求是否命中黑名单。
     *
     * @param request 投放请求
     * @return 是否被拦截
     */
    private boolean isBlocked(DeliveryRequest request) {
        return isBlocked(request.getUserAgent(), request.getIp());
    }

    /**
     * 判断 UA 关键字与 IP 前缀黑名单。
     *
     * @param userAgent UA
     * @param ip        IP
     * @return 是否被拦截
     */
    private boolean isBlocked(String userAgent, String ip) {
        String ua = userAgent == null ? "" : userAgent.toLowerCase(Locale.ROOT);
        for (String keyword : blockedUaKeywords.split(",")) {
            if (!keyword.isBlank() && ua.contains(keyword.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        String address = ip == null ? "" : ip;
        for (String prefix : blockedIpPrefixes.split(",")) {
            if (!prefix.isBlank() && address.startsWith(prefix.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * null 字符串归一成空串，避免 NOT NULL 列插入失败。
     *
     * @param value 原值
     * @return 非空字符串
     */
    private String text(String value) {
        return value == null ? "" : value;
    }

    /**
     * 参与排序的候选快照。
     *
     * @param campaign 计划
     * @param unit     单元
     * @param creative 创意
     * @param score    竞价值
     */
    private record Ranked(AdCampaign campaign, AdUnit unit, AdCreative creative, long score) {
    }

}
