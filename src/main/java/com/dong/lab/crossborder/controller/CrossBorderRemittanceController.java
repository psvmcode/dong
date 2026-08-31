package com.dong.lab.crossborder.controller;

import com.dong.lab.common.result.PageResult;
import com.dong.lab.common.result.Result;
import com.dong.lab.crossborder.dto.ComplianceRecordResponse;
import com.dong.lab.crossborder.dto.RemittanceCreateRequest;
import com.dong.lab.crossborder.dto.RemittanceResponse;
import com.dong.lab.crossborder.enums.RemittanceStatus;
import com.dong.lab.crossborder.service.ComplianceService;
import com.dong.lab.crossborder.service.RemittanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 跨境汇款主流程。发起一笔汇款会依次经过幂等校验、合规筛查、锁汇、扣款，
 * 清算由消息队列异步推进。
 */
@RestController
@RequestMapping("/api/crossborder/remittance")
@RequiredArgsConstructor
@Tag(name = "跨境支付-汇款")
public class CrossBorderRemittanceController {

    private final RemittanceService remittanceService;

    private final ComplianceService complianceService;

    /**
     * 发起汇款。idempotentKey 由调用方生成，同一键重放会返回原单而不是重复汇款。
     */
    @PostMapping
    @Operation(summary = "发起跨境汇款，含合规筛查、锁汇与扣款")
    public Result<RemittanceResponse> create(@Valid @RequestBody RemittanceCreateRequest request) {
        return Result.success(remittanceService.create(request));
    }

    /**
     * 按汇款单号查询。
     */
    @GetMapping("/{remittanceNo}")
    @Operation(summary = "按汇款单号查询")
    public Result<RemittanceResponse> findByRemittanceNo(@PathVariable String remittanceNo) {
        return Result.success(remittanceService.findByRemittanceNo(remittanceNo));
    }

    /**
     * 按幂等键查询，用于超时后确认是否已受理。
     */
    @GetMapping("/by-idempotent/{idempotentKey}")
    @Operation(summary = "按幂等键查询，用于超时重试后的确认")
    public Result<RemittanceResponse> findByIdempotentKey(@PathVariable String idempotentKey) {
        return Result.success(remittanceService.findByIdempotentKey(idempotentKey));
    }

    /**
     * 分页查询。
     */
    @GetMapping
    @Operation(summary = "分页查询汇款单，可按状态过滤")
    public Result<PageResult<RemittanceResponse>> findByPage(
            @RequestParam(required = false) RemittanceStatus status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(remittanceService.findByPage(status, pageNum, pageSize));
    }

    /**
     * 查询某笔汇款的合规检查记录，逐条留痕。
     */
    @GetMapping("/{remittanceNo}/compliance")
    @Operation(summary = "查询汇款单的合规检查记录")
    public Result<List<ComplianceRecordResponse>> compliance(@PathVariable String remittanceNo) {
        return Result.success(complianceService.recordsOf(remittanceNo));
    }

    /**
     * 按批次查询汇款单。
     */
    @GetMapping("/by-batch/{batchNo}")
    @Operation(summary = "按清算批次查询汇款单")
    public Result<List<RemittanceResponse>> findByBatchNo(@PathVariable String batchNo) {
        return Result.success(remittanceService.findByBatchNo(batchNo));
    }

    /**
     * 运行时统计，含各状态单量、幂等命中、合规拒绝与消息计数。
     */
    @GetMapping("/runtime")
    @Operation(summary = "查看汇款运行时统计")
    public Result<Map<String, Object>> runtime() {
        return Result.success(remittanceService.runtime());
    }

}
