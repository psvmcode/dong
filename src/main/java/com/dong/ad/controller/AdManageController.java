package com.dong.ad.controller;

import com.dong.ad.dto.AdActionRequest;
import com.dong.ad.dto.AdCreativeCreateRequest;
import com.dong.ad.dto.AdQuery;
import com.dong.ad.dto.AdUnitCreateRequest;
import com.dong.ad.dto.AdUnitUpdateRequest;
import com.dong.ad.dto.AdvertiserCreateRequest;
import com.dong.ad.dto.CampaignCreateRequest;
import com.dong.ad.dto.CampaignResponse;
import com.dong.ad.dto.CampaignUpdateRequest;
import com.dong.ad.handler.AdChargeEventHandler;
import com.dong.ad.service.AdManageService;
import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.common.result.PageResult;
import com.dong.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 广告投放配置管理接口。
 *
 * <p>配置数据一律走 JSON body；状态迁移用统一动作接口而不是给每个事件建一条路径，
 * 事件名放在请求体里，新增生命周期事件时不用再改 URL 约定。
 */
@RestController
@Validated
@RequestMapping("/api/ad/manage")
@RequiredArgsConstructor
@Tag(name = "广告投放-配置管理")
public class AdManageController {

    /**
     * adManageService，业务服务层。
     */
    private final AdManageService adManageService;

    /**
     * chargeEventHandler，消费侧计数查询。
     */
    private final AdChargeEventHandler chargeEventHandler;

    /**
     * 模块开关，关闭时所有接口返回 1004。
     */
    @Value("${dong.ad.enabled:true}")
    private boolean enabled;

    /**
     * 开立广告主账户。
     */
    @PostMapping("/advertiser/create")
    @Operation(summary = "开立广告主账户并初始充值")
    public Result<Long> createAdvertiser(@Valid @RequestBody AdvertiserCreateRequest request) {
        checkAvailable();
        return Result.success(adManageService.createAdvertiser(request));
    }

    /**
     * 账户充值。
     */
    @PostMapping("/advertiser/recharge")
    @Operation(summary = "广告主账户充值，返回充值后余额")
    public Result<Long> recharge(@Valid @RequestBody AdActionRequest request) {
        checkAvailable();
        return Result.success(adManageService.recharge(request));
    }

    /**
     * 创建推广计划。
     */
    @PostMapping("/campaign/create")
    @Operation(summary = "创建推广计划，含日预算、总预算与投放时段")
    public Result<Long> createCampaign(@Valid @RequestBody CampaignCreateRequest request) {
        checkAvailable();
        return Result.success(adManageService.createCampaign(request));
    }

    /**
     * 调整计划配置。
     */
    @PostMapping("/campaign/update")
    @Operation(summary = "调整日预算、总预算、结束日期与投放时段")
    public Result<Void> updateCampaign(@Valid @RequestBody CampaignUpdateRequest request) {
        checkAvailable();
        adManageService.updateCampaign(request);
        return Result.success();
    }

    /**
     * 查询计划详情。
     */
    @PostMapping("/campaign/detail")
    @Operation(summary = "查询计划详情，含今日已花费")
    public Result<CampaignResponse> campaignDetail(@Valid @RequestBody AdActionRequest request) {
        checkAvailable();
        return Result.success(adManageService.campaign(request.getId()));
    }

    /**
     * 推进计划生命周期。
     */
    @PostMapping("/campaign/transition")
    @Operation(summary = "推进计划状态：提交、审核、上线、暂停、恢复、结束")
    public Result<CampaignResponse> transition(@Valid @RequestBody AdActionRequest request) {
        checkAvailable();
        return Result.success(adManageService.transition(request));
    }

    /**
     * 创建广告单元。
     */
    @PostMapping("/unit/create")
    @Operation(summary = "创建广告单元，设定定向、计费类型与出价")
    public Result<Long> createUnit(@Valid @RequestBody AdUnitCreateRequest request) {
        checkAvailable();
        return Result.success(adManageService.createUnit(request));
    }

    /**
     * 调整单元配置。
     */
    @PostMapping("/unit/update")
    @Operation(summary = "调整单元出价、保留价、预估点击率与定向")
    public Result<Void> updateUnit(@Valid @RequestBody AdUnitUpdateRequest request) {
        checkAvailable();
        adManageService.updateUnit(request);
        return Result.success();
    }

    /**
     * 启用或暂停单元。
     */
    @PostMapping("/unit/toggle")
    @Operation(summary = "启用或暂停广告单元")
    public Result<Void> toggleUnit(@Valid @RequestBody AdActionRequest request) {
        checkAvailable();
        adManageService.toggleUnit(request);
        return Result.success();
    }

    /**
     * 创建创意。
     */
    @PostMapping("/creative/create")
    @Operation(summary = "创建广告创意，初始为待审核")
    public Result<Long> createCreative(@Valid @RequestBody AdCreativeCreateRequest request) {
        checkAvailable();
        return Result.success(adManageService.createCreative(request));
    }

    /**
     * 审核创意。
     */
    @PostMapping("/creative/review")
    @Operation(summary = "审核创意通过或驳回，驳回必须给原因")
    public Result<Void> reviewCreative(@Valid @RequestBody AdActionRequest request) {
        checkAvailable();
        adManageService.reviewCreative(request);
        return Result.success();
    }

    /**
     * 通用分页查询。
     */
    @PostMapping("/query")
    @Operation(summary = "按 target 分页查询广告主、计划、单元或创意")
    public Result<PageResult<Object>> query(@Valid @RequestBody AdQuery request) {
        checkAvailable();
        return Result.success(adManageService.query(request));
    }

    /**
     * 查看模块运行时状态。
     */
    @PostMapping("/runtime")
    @Operation(summary = "查看配置模块运行时状态与消息通道")
    public Result<Map<String, Object>> runtime() {
        checkAvailable();
        Map<String, Object> runtime = new LinkedHashMap<>(adManageService.runtime());
        runtime.put("chargeConsumer", chargeEventHandler.stats());
        return Result.success(runtime);
    }

    /**
     * 模块开关未开时统一抛 1004，避免把底层异常暴露成 5000。
     */
    private void checkAvailable() {
        if (!enabled) {
            throw new BusinessException(Constants.CODE_MIDDLEWARE_DISABLED, "ad module is disabled");
        }
    }

}
