package com.dong.ad.controller;

import com.dong.ad.dto.BurstLabRequest;
import com.dong.ad.dto.BurstLabResponse;
import com.dong.ad.dto.DailyReportQuery;
import com.dong.ad.dto.DailyReportResponse;
import com.dong.ad.dto.ReconcileRequest;
import com.dong.ad.dto.ReconcileResponse;
import com.dong.ad.service.AdLabService;
import com.dong.ad.service.AdSettlementService;
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

/**
 * 投放报表、对账与并发对照实验接口。
 */
@RestController
@Validated
@RequestMapping("/api/ad/report")
@RequiredArgsConstructor
@Tag(name = "广告投放-报表与对账")
public class AdReportController {

    /**
     * adSettlementService，业务服务层。
     */
    private final AdSettlementService adSettlementService;

    /**
     * adLabService，业务服务层。
     */
    private final AdLabService adLabService;

    /**
     * 模块开关，关闭时所有接口返回 1004。
     */
    @Value("${dong.ad.enabled:true}")
    private boolean enabled;

    /**
     * 查询日报表。
     */
    @PostMapping("/daily")
    @Operation(summary = "分页查询投放日报表，含 CTR、平均点击成本与超投标记")
    public Result<PageResult<DailyReportResponse>> daily(@Valid @RequestBody DailyReportQuery request) {
        checkAvailable();
        return Result.success(adSettlementService.dailyReport(request));
    }

    /**
     * 手动触发对账。
     */
    @PostMapping("/reconcile")
    @Operation(summary = "按流水重算某天汇总、重建预算副本并补投漏单")
    public Result<ReconcileResponse> reconcile(@RequestBody ReconcileRequest request) {
        checkAvailable();
        ReconcileRequest condition = request == null ? new ReconcileRequest() : request;
        boolean resend = !Boolean.FALSE.equals(condition.getResendPending());
        return Result.success(adSettlementService.reconcile(condition.getStatDate(), resend));
    }

    /**
     * 并发计费对照实验。
     */
    @PostMapping("/lab/burst")
    @Operation(summary = "并发计费对照实验，对比 Redis 预扣与纯数据库扣减的耗时与超投情况")
    public Result<BurstLabResponse> burst(@Valid @RequestBody BurstLabRequest request) {
        checkAvailable();
        return Result.success(adLabService.burst(request));
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
