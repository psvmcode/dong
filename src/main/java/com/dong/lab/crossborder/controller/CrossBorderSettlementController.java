package com.dong.lab.crossborder.controller;

import com.dong.lab.common.result.Result;
import com.dong.lab.crossborder.dto.ReconDiffResponse;
import com.dong.lab.crossborder.dto.SettlementBatchResponse;
import com.dong.lab.crossborder.enums.SettlementChannel;
import com.dong.lab.crossborder.enums.SettlementStatus;
import com.dong.lab.crossborder.mapper.ReconDiffMapper;
import com.dong.lab.crossborder.service.SettlementService;
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
 * 清算与对账。清算按批次走，渠道有固定的清算窗口；
 * 对账把渠道回单与本地流水比对，差异逐笔记账由运营处理。
 */
@RestController
@RequestMapping("/api/crossborder/settlement")
@RequiredArgsConstructor
@Tag(name = "跨境支付-清算")

public class CrossBorderSettlementController {

    /**
     * settlementService，业务服务层。
     */
    private final SettlementService settlementService;

    /**
     * reconDiffMapper，MyBatis Mapper 数据访问层。
     */
    private final ReconDiffMapper reconDiffMapper;

    /**
     * 创建清算批次。
     */
    @PostMapping("/batch")
    @Operation(summary = "创建清算批次，指定渠道与清算截止时间")
    public Result<String> createBatch(@RequestParam SettlementChannel channel,
                                      @RequestParam String currency,
                                      @RequestParam(defaultValue = "10") long cutoffMinutes) {
        return Result.success(settlementService.createBatch(channel, currency, cutoffMinutes));
    }

    /**
     * 查询批次详情。
     */
    @GetMapping("/batch/{batchNo}")
    @Operation(summary = "查询清算批次详情")
    public Result<SettlementBatchResponse> findByBatchNo(@PathVariable String batchNo) {
        return Result.success(settlementService.findByBatchNo(batchNo));
    }

    /**
     * 查询全部批次。
     */
    @GetMapping("/batch")
    @Operation(summary = "查询全部清算批次")
    public Result<List<SettlementBatchResponse>> findAll() {
        return Result.success(settlementService.findAll());
    }

    /**
     * 把已扣款的汇款单并入批次。
     */
    @PostMapping("/batch/{batchNo}/collect")
    @Operation(summary = "把已扣款的汇款单并入批次")
    public Result<Integer> collect(@PathVariable String batchNo,
                                   @RequestParam(defaultValue = "100") int limit) {
        return Result.success(settlementService.collect(batchNo, limit));
    }

    /**
     * 执行清算，给收款方入账。
     */
    @PostMapping("/batch/{batchNo}/settle")
    @Operation(summary = "执行清算，给收款方入账并推进状态")
    public Result<Integer> settle(@PathVariable String batchNo) {
        return Result.success(settlementService.settle(batchNo));
    }

    /**
     * 手工关闭到期批次。定时任务会自动执行。
     */
    @PostMapping("/close-overdue")
    @Operation(summary = "关闭超过清算截止时间的批次")
    public Result<Integer> closeOverdue() {
        return Result.success(settlementService.closeOverdue());
    }

    /**
     * 查询对账差异。长款短款会直接造成资金损失，必须当日处理。
     */
    @GetMapping("/recon")
    @Operation(summary = "查询对账差异，可按批次过滤")
    public Result<Map<String, Object>> recon(@RequestParam(required = false) String batchNo) {
        List<ReconDiffResponse> diffs = (batchNo == null || batchNo.isBlank()
                ? reconDiffMapper.selectAll(100)
                : reconDiffMapper.selectByBatchNo(batchNo)).stream()
                .map(ReconDiffResponse::from)
                .toList();
        return Result.success(Map.of(
                "count", diffs.size(),
                "unhandled", reconDiffMapper.countUnhandled(),
                "diffs", diffs));
    }

    /**
     * 查询批次状态分布，便于观察清算进度。
     */
    @GetMapping("/status")
    @Operation(summary = "查询清算批次状态分布")
    public Result<Map<String, Object>> statusDistribution() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (SettlementStatus status : SettlementStatus.values()) {
            result.put(status.name(), settlementService.findAll().stream()
                    .filter(b -> b.getStatus() == status)
                    .count());
        }
        return Result.success(result);
    }

}
