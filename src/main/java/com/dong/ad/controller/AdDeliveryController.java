package com.dong.ad.controller;

import com.dong.ad.dto.AdEventRequest;
import com.dong.ad.dto.AdEventResponse;
import com.dong.ad.dto.AdQuery;
import com.dong.ad.dto.DeliveryRequest;
import com.dong.ad.dto.DeliveryResponse;
import com.dong.ad.enums.AdEventType;
import com.dong.ad.service.AdDeliveryService;
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

import java.util.Map;

/**
 * 广告投放链路接口。
 *
 * <p>请求、曝光、点击三步靠 traceId 串起来。事件上报只带链路 id 与幂等键，
 * 价格与计划归属一律取自服务端签发的令牌，不接受客户端传入。
 */
@RestController
@Validated
@RequestMapping("/api/ad/delivery")
@RequiredArgsConstructor
@Tag(name = "广告投放-投放链路")
public class AdDeliveryController {

    /**
     * adDeliveryService，业务服务层。
     */
    private final AdDeliveryService adDeliveryService;

    /**
     * 模块开关，关闭时所有接口返回 1004。
     */
    @Value("${dong.ad.enabled:true}")
    private boolean enabled;

    /**
     * 广告位请求。
     */
    @PostMapping("/request")
    @Operation(summary = "发起广告请求，返回胜出创意与链路 id")
    public Result<DeliveryResponse> request(@Valid @RequestBody DeliveryRequest request) {
        checkAvailable();
        return Result.success(adDeliveryService.request(request));
    }

    /**
     * 上报曝光。
     */
    @PostMapping("/event/impression")
    @Operation(summary = "上报曝光事件，CPM 计划在此计费")
    public Result<AdEventResponse> impression(@Valid @RequestBody AdEventRequest request) {
        checkAvailable();
        return Result.success(adDeliveryService.track(request, AdEventType.IMPRESSION));
    }

    /**
     * 上报点击。
     */
    @PostMapping("/event/click")
    @Operation(summary = "上报点击事件，CPC 计划在此计费")
    public Result<AdEventResponse> click(@Valid @RequestBody AdEventRequest request) {
        checkAvailable();
        return Result.success(adDeliveryService.track(request, AdEventType.CLICK));
    }

    /**
     * 查询投放事件。
     */
    @PostMapping("/event/query")
    @Operation(summary = "分页查询投放事件与令牌，按链路排查问题")
    public Result<PageResult<Object>> events(@Valid @RequestBody AdQuery request) {
        checkAvailable();
        return Result.success(adDeliveryService.events(request));
    }

    /**
     * 查看链路运行时状态。
     */
    @PostMapping("/runtime")
    @Operation(summary = "查看投放链路运行时状态与降级次数")
    public Result<Map<String, Object>> runtime() {
        checkAvailable();
        return Result.success(adDeliveryService.runtime());
    }

    /**
     * 模块开关未开时统一抛 1004。
     */
    private void checkAvailable() {
        if (!enabled) {
            throw new BusinessException(Constants.CODE_MIDDLEWARE_DISABLED, "ad module is disabled");
        }
    }

}
