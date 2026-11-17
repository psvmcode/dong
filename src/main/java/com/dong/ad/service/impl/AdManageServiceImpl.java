package com.dong.ad.service.impl;

import com.dong.ad.dto.AdActionRequest;
import com.dong.ad.dto.AdCreativeCreateRequest;
import com.dong.ad.dto.AdCreativeResponse;
import com.dong.ad.dto.AdQuery;
import com.dong.ad.dto.AdUnitCreateRequest;
import com.dong.ad.dto.AdUnitResponse;
import com.dong.ad.dto.AdUnitUpdateRequest;
import com.dong.ad.dto.AdvertiserCreateRequest;
import com.dong.ad.dto.AdvertiserResponse;
import com.dong.ad.dto.CampaignCreateRequest;
import com.dong.ad.dto.CampaignResponse;
import com.dong.ad.dto.CampaignUpdateRequest;
import com.dong.ad.entity.AdAdvertiser;
import com.dong.ad.entity.AdCampaign;
import com.dong.ad.entity.AdCreative;
import com.dong.ad.entity.AdDailyStat;
import com.dong.ad.entity.AdUnit;
import com.dong.ad.enums.AdBidType;
import com.dong.ad.enums.AdCampaignEvent;
import com.dong.ad.enums.AdCampaignStatus;
import com.dong.ad.enums.AdCreativeStatus;
import com.dong.ad.enums.AdSwitchStatus;
import com.dong.ad.mapper.AdAdvertiserDailyMapper;
import com.dong.ad.mapper.AdAdvertiserMapper;
import com.dong.ad.mapper.AdCampaignMapper;
import com.dong.ad.mapper.AdCreativeMapper;
import com.dong.ad.mapper.AdDailyStatMapper;
import com.dong.ad.mapper.AdUnitMapper;
import com.dong.ad.service.AdBudgetService;
import com.dong.ad.service.AdAuditService;
import com.dong.ad.service.AdManageService;
import com.dong.ad.service.AdSettlementService;
import com.dong.ad.support.AdCandidateCache;
import com.dong.ad.support.AdRuntimeStats;
import com.dong.ad.support.AdTargetingSupport;
import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.common.result.PageRequest;
import com.dong.common.result.PageResult;
import com.dong.common.util.JsonUtils;
import com.dong.common.util.Snowflake;
import com.dong.framework.mq.MqFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 广告投放配置管理实现。
 *
 * <p>配置写入一律带乐观锁或条件更新：两个人同时改预算、同时点上线、
 * 同时审核一条创意，都是后台里真实会发生的并发。
 * 靠「先查再改」会让后写的人悄悄覆盖前一个人的结果且没有任何提示。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdManageServiceImpl implements AdManageService {

    private static final String ADVERTISER_NO_PREFIX = "ADV";

    private static final String CAMPAIGN_NO_PREFIX = "CMP";

    private static final String UNIT_NO_PREFIX = "UNT";

    private static final String CREATIVE_NO_PREFIX = "CRT";

    private static final String ALL_DAY = "111111111111111111111111";

    /**
     * adAdvertiserMapper，MyBatis Mapper 数据访问层。
     */
    private final AdAdvertiserMapper adAdvertiserMapper;

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
     * adDailyStatMapper，MyBatis Mapper 数据访问层。
     */
    private final AdDailyStatMapper adDailyStatMapper;

    /**
     * adAdvertiserDailyMapper，MyBatis Mapper 数据访问层。
     */
    private final AdAdvertiserDailyMapper adAdvertiserDailyMapper;

    /**
     * adSettlementService，业务服务层。
     */
    private final AdSettlementService adSettlementService;

    /**
     * adBudgetService，业务服务层。
     */
    private final AdBudgetService adBudgetService;

    /**
     * adAuditService，配置变更审计。
     */
    private final AdAuditService adAuditService;

    /**
     * candidateCache，召回候选缓存，配置变更后必须失效。
     */
    private final AdCandidateCache candidateCache;

    /**
     * targetingSupport，定向条件工具。
     */
    private final AdTargetingSupport targetingSupport;

    /**
     * runtimeStats，链路计数。
     */
    private final AdRuntimeStats runtimeStats;

    /**
     * mqFacade，消息通道状态查询。
     */
    private final MqFacade mqFacade;

    /**
     * 雪花 ID 生成器。
     */
    private final Snowflake snowflake;

    /**
     * 模块开关，用于运行时状态展示。
     */
    @Value("${dong.ad.enabled:true}")
    private boolean enabled;

    /**
     * 开立广告主账户。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAdvertiser(AdvertiserCreateRequest request) {
        AdAdvertiser advertiser = new AdAdvertiser();
        advertiser.setAdvertiserNo(ADVERTISER_NO_PREFIX + snowflake.nextId());
        advertiser.setName(request.getName().trim());
        advertiser.setBalanceCents(request.getBalanceCents());
        advertiser.setDailyLimitCents(0L);
        advertiser.setStatus(AdSwitchStatus.ON.getCode());
        advertiser.setVersion(0);
        adAdvertiserMapper.insert(advertiser);
        log.info("ad advertiser created id={} balance={}", advertiser.getId(), advertiser.getBalanceCents());
        return advertiser.getId();
    }

    /**
     * 账户充值。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long recharge(AdActionRequest request) {
        if (request.getAmountCents() == null || request.getAmountCents() <= 0L) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "amountCents must be positive");
        }
        AdAdvertiser before = requireAdvertiser(request.getId());
        int updated = adAdvertiserMapper.addBalance(request.getId(), request.getAmountCents(), AdSwitchStatus.ON.getCode());
        if (updated == 0) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "advertiser is frozen");
        }
        long after = adAdvertiserMapper.selectById(request.getId()).getBalanceCents();
        adAuditService.write("system", "recharge", "advertiser", request.getId(),
                JsonUtils.toJson(Map.of("balanceCents", before.getBalanceCents())),
                JsonUtils.toJson(Map.of("balanceCents", after)), request.getRemark());
        log.info("ad advertiser recharged id={} amount={}", request.getId(), request.getAmountCents());
        return after;
    }

    /**
     * 调整账户日限额。0 表示不限，当天快照同步刷新，避免条件扣减仍按旧限额挡着。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAdvertiserDailyLimit(Long id, long dailyLimitCents) {
        if (dailyLimitCents < 0L || dailyLimitCents > Constants.MAX_MONEY_CENTS) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "invalid daily limit");
        }
        AdAdvertiser before = requireAdvertiser(id);
        int updated = adAdvertiserMapper.updateDailyLimit(id, dailyLimitCents, before.getVersion());
        if (updated == 0) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "advertiser was modified by someone else");
        }
        adAdvertiserDailyMapper.updateLimitSnapshot(LocalDate.now(), id, dailyLimitCents);
        adAuditService.write("system", "update_daily_limit", "advertiser", id,
                JsonUtils.toJson(Map.of("dailyLimitCents", String.valueOf(before.getDailyLimitCents()))),
                JsonUtils.toJson(Map.of("dailyLimitCents", dailyLimitCents)), "");
        log.info("ad advertiser daily limit updated id={} limit={}", id, dailyLimitCents);
    }

    /**
     * 创建推广计划。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCampaign(CampaignCreateRequest request) {
        AdAdvertiser advertiser = requireAdvertiser(request.getAdvertiserId());
        if (advertiser.getStatus() != AdSwitchStatus.ON.getCode()) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "advertiser is frozen");
        }
        if (request.getDailyBudgetCents() > request.getTotalBudgetCents()) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "daily budget exceeds total budget");
        }
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "start date is after end date");
        }
        AdCampaign campaign = new AdCampaign();
        campaign.setCampaignNo(CAMPAIGN_NO_PREFIX + snowflake.nextId());
        campaign.setAdvertiserId(request.getAdvertiserId());
        campaign.setName(request.getName().trim());
        campaign.setDailyBudgetCents(request.getDailyBudgetCents());
        campaign.setTotalBudgetCents(request.getTotalBudgetCents());
        campaign.setSpentTotalCents(0L);
        campaign.setStartDate(request.getStartDate());
        campaign.setEndDate(request.getEndDate());
        campaign.setDeliveryHours(normalizeHours(request.getDeliveryHours()));
        campaign.setStatus(AdCampaignStatus.DRAFT);
        campaign.setVersion(0);
        adCampaignMapper.insert(campaign);
        log.info("ad campaign created id={} advertiser={} dailyBudget={}", campaign.getId(), campaign.getAdvertiserId(), campaign.getDailyBudgetCents());
        return campaign.getId();
    }

    /**
     * 调整计划配置。改日预算必须同步刷新当天快照并重建 Redis 副本，
     * 否则条件扣减仍按旧预算挡着，新预算当天根本花不出去。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCampaign(CampaignUpdateRequest request) {
        AdCampaign current = requireCampaign(request.getId());
        if (current.getStatus() == AdCampaignStatus.FINISHED) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "finished campaign cannot be modified");
        }
        if (request.getDailyBudgetCents() != null && request.getDailyBudgetCents() > (request.getTotalBudgetCents() == null ? current.getTotalBudgetCents() : request.getTotalBudgetCents())) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "daily budget exceeds total budget");
        }
        if (request.getEndDate() != null && request.getEndDate().isBefore(current.getStartDate())) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "end date is before start date");
        }
        AdCampaign patch = new AdCampaign();
        patch.setId(request.getId());
        patch.setDailyBudgetCents(request.getDailyBudgetCents());
        patch.setTotalBudgetCents(request.getTotalBudgetCents());
        patch.setEndDate(request.getEndDate());
        patch.setDeliveryHours(request.getDeliveryHours() == null ? null : normalizeHours(request.getDeliveryHours()));
        int updated = adCampaignMapper.updateConfig(patch, current.getVersion());
        if (updated == 0) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "campaign was modified by someone else");
        }
        if (request.getDailyBudgetCents() != null) {
            LocalDate today = LocalDate.now();
            long budget = request.getDailyBudgetCents();
            adDailyStatMapper.updateBudgetSnapshot(today, request.getId(), budget);
            try {
                adBudgetService.rebuild(request.getId(), today, budget, adSettlementService.todayCharge(request.getId()));
            } catch (Exception ex) {
                log.warn("rebuild ad budget replica failed campaign={}", request.getId(), ex);
            }
        }
        candidateCache.invalidate();
        adAuditService.write("system", "update_campaign", "campaign", request.getId(),
                campaignSnapshot(current), campaignSnapshot(requireCampaign(request.getId())), "");
        log.info("ad campaign config updated id={}", request.getId());
    }

    /**
     * 查询计划详情。
     */
    @Override
    public CampaignResponse campaign(Long id) {
        return CampaignResponse.from(requireCampaign(id), adSettlementService.todayCharge(id));
    }

    /**
     * 推进计划生命周期状态。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampaignResponse transition(AdActionRequest request) {
        AdCampaign current = requireCampaign(request.getId());
        AdCampaignEvent event = AdCampaignEvent.ofName(request.getEvent());
        AdCampaignStatus target = current.getStatus().next(event);
        if (target == null) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "illegal transition from " + current.getStatus() + " on " + event);
        }
        if (event == AdCampaignEvent.REJECT && (request.getRemark() == null || request.getRemark().isBlank())) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "reject reason is required");
        }
        if (event == AdCampaignEvent.START) {
            checkStartGuard(current);
        }
        int updated = adCampaignMapper.updateStatus(request.getId(), target.getCode(), current.getStatus().getCode(), current.getVersion());
        if (updated == 0) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "campaign was modified by someone else");
        }
        candidateCache.invalidate();
        adAuditService.write("system", "transition:" + event.name(), "campaign", request.getId(),
                campaignSnapshot(current), campaignSnapshot(requireCampaign(request.getId())), request.getRemark());
        log.info("ad campaign transition id={} from={} to={} event={}", request.getId(), current.getStatus(), target, event);
        return CampaignResponse.from(requireCampaign(request.getId()), adSettlementService.todayCharge(request.getId()));
    }

    /**
     * 创建广告单元。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createUnit(AdUnitCreateRequest request) {
        requireCampaign(request.getCampaignId());
        AdBidType.of(request.getBidType());
        AdUnit unit = new AdUnit();
        unit.setUnitNo(UNIT_NO_PREFIX + snowflake.nextId());
        unit.setCampaignId(request.getCampaignId());
        unit.setName(request.getName().trim());
        unit.setTargeting(targetingSupport.toJson(request.getRegions(), request.getTags(), request.getDevices(), request.getKeywords()));
        unit.setBidType(request.getBidType());
        unit.setBidCents(request.getBidCents());
        unit.setFloorCents(request.getFloorCents() == null ? 1L : request.getFloorCents());
        unit.setPctr(request.getPctr());
        unit.setStatus(AdSwitchStatus.ON.getCode());
        unit.setVersion(0);
        adUnitMapper.insert(unit);
        candidateCache.invalidate();
        adAuditService.write("system", "create_unit", "unit", unit.getId(), "",
                JsonUtils.toJson(Map.of("campaignId", unit.getCampaignId(), "bidType", unit.getBidType(), "bidCents", unit.getBidCents())), "");
        log.info("ad unit created id={} campaign={} bidType={} bid={}", unit.getId(), unit.getCampaignId(), unit.getBidType(), unit.getBidCents());
        return unit.getId();
    }

    /**
     * 调整单元出价与定向。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUnit(AdUnitUpdateRequest request) {
        AdUnit current = requireUnit(request.getId());
        boolean targetingChanged = request.getRegions() != null || request.getTags() != null
                || request.getDevices() != null || request.getKeywords() != null;
        AdUnit patch = new AdUnit();
        patch.setId(request.getId());
        patch.setName(request.getName());
        patch.setTargeting(targetingChanged ? targetingSupport.merge(current.getTargeting(), request) : null);
        patch.setBidCents(request.getBidCents());
        patch.setFloorCents(request.getFloorCents());
        patch.setPctr(request.getPctr());
        int updated = adUnitMapper.updateConfig(patch, current.getVersion());
        if (updated == 0) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "unit was modified by someone else");
        }
        candidateCache.invalidate();
        adAuditService.write("system", "update_unit", "unit", request.getId(),
                JsonUtils.toJson(Map.of("bidCents", current.getBidCents(), "pctr", current.getPctr())),
                JsonUtils.toJson(Map.of("bidCents", String.valueOf(request.getBidCents()), "pctr", String.valueOf(request.getPctr()))), "");
        log.info("ad unit config updated id={}", request.getId());
    }

    /**
     * 启用或暂停单元。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleUnit(AdActionRequest request) {
        if (request.getEnabled() == null) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "enabled is required");
        }
        AdUnit unit = requireUnit(request.getId());
        AdSwitchStatus status = AdSwitchStatus.of(request.getEnabled());
        int updated = adUnitMapper.updateStatus(request.getId(), status.getCode(), unit.getVersion());
        if (updated == 0) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "unit was modified by someone else");
        }
        candidateCache.invalidate();
        adAuditService.write("system", "toggle_unit", "unit", request.getId(),
                JsonUtils.toJson(Map.of("status", AdSwitchStatus.of(unit.getStatus()).name())),
                JsonUtils.toJson(Map.of("status", status.name())), request.getRemark());
        log.info("ad unit toggled id={} status={}", request.getId(), status);
    }

    /**
     * 创建广告创意。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCreative(AdCreativeCreateRequest request) {
        requireUnit(request.getUnitId());
        AdCreative creative = new AdCreative();
        creative.setCreativeNo(CREATIVE_NO_PREFIX + snowflake.nextId());
        creative.setUnitId(request.getUnitId());
        creative.setTitle(request.getTitle().trim());
        creative.setImageUrl(request.getImageUrl() == null ? "" : request.getImageUrl().trim());
        creative.setLandingUrl(request.getLandingUrl().trim());
        creative.setStatus(AdCreativeStatus.PENDING.getCode());
        creative.setRejectReason("");
        creative.setVersion(0);
        adCreativeMapper.insert(creative);
        candidateCache.invalidate();
        adAuditService.write("system", "create_creative", "creative", creative.getId(), "",
                JsonUtils.toJson(Map.of("unitId", creative.getUnitId(), "title", creative.getTitle())), "");
        log.info("ad creative created id={} unit={}", creative.getId(), creative.getUnitId());
        return creative.getId();
    }

    /**
     * 审核创意。驳回必须给原因，否则广告主改都不知道往哪改。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewCreative(AdActionRequest request) {
        AdCreative creative = requireCreative(request.getId());
        String decision = request.getDecision() == null ? "" : request.getDecision().trim().toLowerCase(Locale.ROOT);
        AdCreativeStatus target;
        if ("approve".equals(decision)) {
            target = AdCreativeStatus.APPROVED;
        } else if ("reject".equals(decision)) {
            if (request.getRemark() == null || request.getRemark().isBlank()) {
                throw new BusinessException(Constants.CODE_PARAM_INVALID, "reject reason is required");
            }
            target = AdCreativeStatus.REJECTED;
        } else {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "decision must be approve or reject");
        }
        int updated = adCreativeMapper.updateStatus(request.getId(), target.getCode(),
                target == AdCreativeStatus.REJECTED ? request.getRemark() : "", creative.getVersion());
        if (updated == 0) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "creative was modified by someone else");
        }
        candidateCache.invalidate();
        adAuditService.write("system", "review_creative", "creative", request.getId(),
                JsonUtils.toJson(Map.of("status", AdCreativeStatus.of(creative.getStatus()).name())),
                JsonUtils.toJson(Map.of("status", target.name())), request.getRemark());
        log.info("ad creative reviewed id={} status={}", request.getId(), target);
    }

    /**
     * 按 target 分页查询配置数据。查不到返回空集，target 越界直接拒绝。
     */
    @Override
    public PageResult<Object> query(AdQuery query) {
        PageRequest page = query.toPageRequest();
        return switch (query.getTarget().toLowerCase(Locale.ROOT)) {
            case "advertiser" -> advertisers(query, page);
            case "campaign" -> campaigns(query, page);
            case "unit" -> units(query, page);
            case "creative" -> creatives(query, page);
            default -> throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown ad query target " + query.getTarget());
        };
    }

    /**
     * 查看模块运行时状态。
     */
    @Override
    public Map<String, Object> runtime() {
        Map<String, Object> runtime = new LinkedHashMap<>(runtimeStats.snapshot());
        runtime.put("enabled", enabled);
        runtime.put("mq", mqFacade.status());
        runtime.put("advertisers", adAdvertiserMapper.countByPage(null, null));
        runtime.put("campaigns", adCampaignMapper.countByPage(null, null, null));
        return runtime;
    }

    /**
     * 分页查询广告主。
     *
     * @param query 查询条件
     * @param page  分页请求
     * @return 分页结果
     */
    private PageResult<Object> advertisers(AdQuery query, PageRequest page) {
        if (query.getStatus() != null) {
            AdSwitchStatus.of(query.getStatus());
        }
        List<AdAdvertiser> list = adAdvertiserMapper.selectByPage(query.getStatus(), query.getKeyword(), page.getOffset(), page.getPageSize());
        long total = adAdvertiserMapper.countByPage(query.getStatus(), query.getKeyword());
        return PageResult.of(list, total, page).map(AdvertiserResponse::from).map(item -> (Object) item);
    }

    /**
     * 分页查询推广计划。今日消耗用一次批量查询拿回，避免单页 200 条打 200 次数据库。
     *
     * @param query 查询条件
     * @param page  分页请求
     * @return 分页结果
     */
    private PageResult<Object> campaigns(AdQuery query, PageRequest page) {
        if (query.getStatus() != null) {
            AdCampaignStatus.of(query.getStatus());
        }
        List<AdCampaign> list = adCampaignMapper.selectByPage(query.getStatus(), query.getParentId(), query.getKeyword(), page.getOffset(), page.getPageSize());
        long total = adCampaignMapper.countByPage(query.getStatus(), query.getParentId(), query.getKeyword());
        Map<Long, Long> todayCharges = new LinkedHashMap<>();
        if (!list.isEmpty()) {
            List<Long> ids = list.stream().map(AdCampaign::getId).toList();
            for (AdDailyStat stat : adDailyStatMapper.selectByCampaignIdsAndDate(ids, LocalDate.now())) {
                todayCharges.put(stat.getCampaignId(), stat.getChargeCents());
            }
        }
        return PageResult.of(list, total, page)
                .map(entity -> CampaignResponse.from(entity, todayCharges.getOrDefault(entity.getId(), 0L)))
                .map(item -> (Object) item);
    }

    /**
     * 分页查询广告单元。
     *
     * @param query 查询条件
     * @param page  分页请求
     * @return 分页结果
     */
    private PageResult<Object> units(AdQuery query, PageRequest page) {
        if (query.getStatus() != null) {
            AdSwitchStatus.of(query.getStatus());
        }
        List<AdUnit> list = adUnitMapper.selectByPage(query.getParentId(), query.getStatus(), query.getKeyword(), page.getOffset(), page.getPageSize());
        long total = adUnitMapper.countByPage(query.getParentId(), query.getStatus(), query.getKeyword());
        return PageResult.of(list, total, page).map(AdUnitResponse::from).map(item -> (Object) item);
    }

    /**
     * 分页查询广告创意。
     *
     * @param query 查询条件
     * @param page  分页请求
     * @return 分页结果
     */
    private PageResult<Object> creatives(AdQuery query, PageRequest page) {
        if (query.getStatus() != null) {
            AdCreativeStatus.of(query.getStatus());
        }
        List<AdCreative> list = adCreativeMapper.selectByPage(query.getParentId(), query.getStatus(), query.getKeyword(), page.getOffset(), page.getPageSize());
        long total = adCreativeMapper.countByPage(query.getParentId(), query.getStatus(), query.getKeyword());
        return PageResult.of(list, total, page).map(AdCreativeResponse::from).map(item -> (Object) item);
    }

    /**
     * 上线守卫：账户可用、有启用单元、至少一条过审创意、投放窗口未过期。
     * 少任何一条，上线后都会产生「请求有量但一条也投不出去」的空转。
     *
     * @param campaign 计划
     */
    private void checkStartGuard(AdCampaign campaign) {
        AdAdvertiser advertiser = requireAdvertiser(campaign.getAdvertiserId());
        if (advertiser.getStatus() != AdSwitchStatus.ON.getCode() || advertiser.getBalanceCents() <= 0L) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "advertiser is frozen or has no balance");
        }
        if (campaign.getEndDate().isBefore(LocalDate.now())) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "campaign window expired");
        }
        if (campaign.getDailyBudgetCents() <= 0L || campaign.getTotalBudgetCents() <= 0L) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "budget must be positive");
        }
        List<AdUnit> units = adUnitMapper.selectByCampaign(campaign.getId(), Constants.MAX_QUERY_LIMIT);
        boolean hasApprovedCreative = units.stream()
                .filter(unit -> unit.getStatus() == AdSwitchStatus.ON.getCode())
                .anyMatch(unit -> adCreativeMapper.countByUnit(unit.getId(), AdCreativeStatus.APPROVED.getCode()) > 0L);
        if (!hasApprovedCreative) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "no approved creative to deliver");
        }
    }

    /**
     * 校验并归一化投放时段，24 位 0/1 字符串。
     *
     * @param hours 原始时段
     * @return 归一化时段
     */
    private String normalizeHours(String hours) {
        String value = hours == null || hours.isBlank() ? ALL_DAY : hours.trim();
        if (value.length() != 24 || !value.matches("[01]{24}")) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "delivery hours must be 24 bits of 0/1");
        }
        return value;
    }

    /**
     * 按 id 查广告主，不存在抛 1001。
     *
     * @param id 广告主 id
     * @return 广告主
     */
    private AdAdvertiser requireAdvertiser(Long id) {
        AdAdvertiser advertiser = adAdvertiserMapper.selectById(id);
        if (advertiser == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "advertiser " + id + " not found");
        }
        return advertiser;
    }

    /**
     * 生成计划关键字段快照，用于审计「改前改后」。
     *
     * @param campaign 计划
     * @return 快照 JSON
     */
    private String campaignSnapshot(AdCampaign campaign) {
        return JsonUtils.toJson(Map.of(
                "dailyBudgetCents", campaign.getDailyBudgetCents(),
                "totalBudgetCents", campaign.getTotalBudgetCents(),
                "spentTotalCents", campaign.getSpentTotalCents(),
                "endDate", String.valueOf(campaign.getEndDate()),
                "deliveryHours", campaign.getDeliveryHours(),
                "status", campaign.getStatus().name()));
    }

    /**
     * 按 id 查计划，不存在抛 1001。
     *
     * @param id 计划 id
     * @return 计划
     */
    private AdCampaign requireCampaign(Long id) {
        AdCampaign campaign = adCampaignMapper.selectById(id);
        if (campaign == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "campaign " + id + " not found");
        }
        return campaign;
    }

    /**
     * 按 id 查单元，不存在抛 1001。
     *
     * @param id 单元 id
     * @return 单元
     */
    private AdUnit requireUnit(Long id) {
        AdUnit unit = adUnitMapper.selectById(id);
        if (unit == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "unit " + id + " not found");
        }
        return unit;
    }

    /**
     * 按 id 查创意，不存在抛 1001。
     *
     * @param id 创意 id
     * @return 创意
     */
    private AdCreative requireCreative(Long id) {
        AdCreative creative = adCreativeMapper.selectById(id);
        if (creative == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "creative " + id + " not found");
        }
        return creative;
    }

}
