package com.dong.lab.crossborder.controller;

import com.dong.lab.common.result.Result;
import com.dong.lab.crossborder.dto.ReconReportResponse;
import com.dong.lab.crossborder.enums.ReconDiffType;
import com.dong.lab.crossborder.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
/**
 * 对账核销。每日对账把渠道回单与本地流水逐笔比对，
 * 差异记入差异表，运营按差异类型逐笔处理。
 *
 * <p>这是资金系统每日必做的闭环动作：
 * 不对账的系统里，漏入账、多扣款、中间行扣费差异都会被掩盖。
 */
@RestController
@RequestMapping("/api/crossborder/recon")
@RequiredArgsConstructor
@Tag(name = "跨境支付-对账")

public class CrossBorderReconController {

    /**
     * reconciliationService，业务服务层。
     */
    private final ReconciliationService reconciliationService;

    /**
     * 执行对账。取已结算批次的本地流水，模拟渠道回单，逐笔比对生成差异。
     *
     * <p>errorRate 用于模拟渠道差错，0 表示回单完全准确；
     * 注入差错后对账应能发现漏单、金额不一致与多余单，这是对账系统自身的验收手段。
     * 重复对账会先清掉该批次的旧差异，结果以最新一轮为准。
     */
    @PostMapping("/{batchNo}")
    @Operation(summary = "执行一轮对账，返回对账报告，可注入渠道差错率")
    public Result<ReconReportResponse> reconcile(@PathVariable String batchNo,
                                                 @RequestParam(defaultValue = "0.0") double errorRate) {
        return Result.success(reconciliationService.reconcile(batchNo, errorRate));
    }

    /**
     * 模拟渠道回单。可用于人工核对或调试对账逻辑。
     */
    @GetMapping("/{batchNo}/channel-statement")
    @Operation(summary = "模拟渠道回单，可注入差错率")
    public Result<List<Map<String, Object>>> channelStatement(@PathVariable String batchNo,
                                                               @RequestParam(defaultValue = "0.0") double errorRate) {
        return Result.success(reconciliationService.generateChannelStatement(batchNo, errorRate));
    }

    /**
     * 查询某批次的对账报告。
     */
    @GetMapping("/{batchNo}/report")
    @Operation(summary = "查询对账报告")
    public Result<ReconReportResponse> report(@PathVariable String batchNo) {
        return Result.success(reconciliationService.report(batchNo));
    }

    /**
     * 运营处理单笔差异。不同类型对应不同处理方式：
     * 长款挂账、短款追讨、金额不一致核销、缺失补单。
     */
    @PostMapping("/diff/{id}")
    @Operation(summary = "处理单笔对账差异")
    public Result<Map<String, Object>> handleDiff(@PathVariable Long id,
                                                   @RequestParam ReconDiffType diffType,
                                                   @RequestParam(defaultValue = "review") String decision) {
        return Result.success(reconciliationService.handleDiff(id, diffType, decision));
    }

    /**
     * 批量处理某批次全部未处理差异。
     */
    @PostMapping("/{batchNo}/handle-all")
    @Operation(summary = "批量处理某批次全部未处理差异")
    public Result<Integer> handleAll(@PathVariable String batchNo,
                                     @RequestParam(defaultValue = "batch review") String decision) {
        return Result.success(reconciliationService.handleAllUnhandled(batchNo, decision));
    }

    /**
     * 对账总览。含未处理差异总数与按类型分布。
     */
    @GetMapping("/overview")
    @Operation(summary = "查询对账总览")
    public Result<Map<String, Object>> overview() {
        return Result.success(reconciliationService.overview());
    }

}
