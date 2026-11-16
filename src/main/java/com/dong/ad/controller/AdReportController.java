package com.dong.ad.controller;

import com.dong.ad.dto.AdAuditQuery;
import com.dong.ad.dto.AdCleanupRequest;
import com.dong.ad.dto.BurstLabRequest;
import com.dong.ad.dto.BurstLabResponse;
import com.dong.ad.dto.DailyReportQuery;
import com.dong.ad.dto.DailyReportResponse;
import com.dong.ad.dto.ReconcileRequest;
import com.dong.ad.dto.ReconcileResponse;
import com.dong.ad.entity.AdAuditLog;
import com.dong.ad.service.AdAuditService;
import com.dong.ad.service.AdCleanupService;
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

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

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
     * adAuditService，配置变更审计。
     */
    private final AdAuditService adAuditService;

    /**
     * adCleanupService，在线数据归档清理。
     */
    private final AdCleanupService adCleanupService;

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
     * 健康检查。
     */
    @PostMapping("/health")
    @Operation(summary = "检查数据库、Redis、MQ、熔断器状态与待补偿事件积压")
    public Result<Map<String, Object>> health() {
        checkAvailable();
        return Result.success(adSettlementService.health());
    }

    /**
     * 查询配置变更审计轨迹。
     */
    @PostMapping("/audit/query")
    @Operation(summary = "分页查询预算、状态、审核等配置变更轨迹")
    public Result<PageResult<AdAuditLog>> audit(@Valid @RequestBody AdAuditQuery request) {
        checkAvailable();
        return Result.success(adAuditService.query(request.getTargetType(), request.getTargetId(), request.getAction(),
                request.getPageNum(), request.getPageSize()));
    }

    /**
     * 归档过期流水并清理过期事件。
     */
    @PostMapping("/cleanup")
    @Operation(summary = "把过期流水搬到归档表并清理过期事件，保留在线表查询性能")
    public Result<Map<String, Object>> cleanup(@RequestBody AdCleanupRequest request) {
        checkAvailable();
        AdCleanupRequest condition = request == null ? new AdCleanupRequest() : request;
        LocalDate before = condition.getBeforeDate() == null ? LocalDate.now().minusDays(7L) : condition.getBeforeDate();
        int archived = adCleanupService.archiveExpiredCharge(before, condition.getLimit());
        int removed = adCleanupService.deleteExpiredEvents(before, condition.getLimit());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("beforeDate", before);
        result.put("chargeArchived", archived);
        result.put("eventsRemoved", removed);
        return Result.success(result);
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
