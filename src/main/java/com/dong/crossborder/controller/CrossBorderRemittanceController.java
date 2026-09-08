package com.dong.crossborder.controller;

import com.dong.common.result.PageResult;
import com.dong.common.result.Result;
import com.dong.crossborder.dto.ComplianceRecordResponse;
import com.dong.crossborder.dto.RemittanceCreateRequest;
import com.dong.crossborder.dto.RemittanceResponse;
import com.dong.crossborder.dto.ReviewDecisionRequest;
import com.dong.crossborder.enums.RemittanceStatus;
import com.dong.crossborder.service.ComplianceService;
import com.dong.crossborder.service.RemittanceService;
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

    /**
     * remittanceService，业务服务层。
     */
    private final RemittanceService remittanceService;

    /**
     * complianceService，业务服务层。
     */
    private final ComplianceService complianceService;

    /**
     * eventService，业务服务层。
     */
    private final com.dong.crossborder.service.RemittanceEventService eventService;

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
     * 待人工审核的汇款单列表。超过反洗钱阈值的大额汇款会挂起在这里，
     * 合规人员逐单核实来源与用途后放行或驳回。
     */
    @GetMapping("/pending-review")
    @Operation(summary = "查询待人工审核的汇款单")
    public Result<PageResult<RemittanceResponse>> pendingReview(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(remittanceService.findByPage(RemittanceStatus.PENDING_REVIEW, pageNum, pageSize));
    }

    /**
     * 审核放行。放行后单子继续走锁汇、扣款、清算，与自动通过的单子殊途同归。
     * 两个审核员同时放行只有一个生效，其余返回冲突。
     */
    @PostMapping("/{remittanceNo}/review/approve")
    @Operation(summary = "人工审核放行，继续锁汇扣款清算")
    public Result<RemittanceResponse> approveReview(@PathVariable String remittanceNo,
                                                    @Valid @RequestBody ReviewDecisionRequest decision) {
        return Result.success(remittanceService.approveReview(remittanceNo, decision));
    }

    /**
     * 审核驳回。资金尚未扣减，直接进入终态并释放占用的日累计限额。
     */
    @PostMapping("/{remittanceNo}/review/reject")
    @Operation(summary = "人工审核驳回，终态并释放日限额")
    public Result<RemittanceResponse> rejectReview(@PathVariable String remittanceNo,
                                                   @Valid @RequestBody ReviewDecisionRequest decision) {
        return Result.success(remittanceService.rejectReview(remittanceNo, decision));
    }

    /**
     * 退汇。资金已到收款方后发现有问题时原路退回，
     * 只允许对已送达的单子发起，手续费按行业惯例不退。
     */
    @PostMapping("/{remittanceNo}/return")
    @Operation(summary = "发起退汇，资金从收款方退回付款方")
    public Result<RemittanceResponse> returnRemittance(@PathVariable String remittanceNo,
                                                       @RequestParam(defaultValue = "") String reason,
                                                       @RequestParam(defaultValue = "system") String operator) {
        return Result.success(remittanceService.returnRemittance(remittanceNo, reason, operator));
    }

    /**
     * 查询流转历史。状态字段只回答「现在是什么状态」，
     * 这里回答「它怎么变成这样的」，排查与审计都靠它。
     */
    @GetMapping("/{remittanceNo}/events")
    @Operation(summary = "查询汇款单的完整状态流转历史")
    public Result<List<com.dong.crossborder.dto.RemittanceEventResponse>> events(
            @PathVariable String remittanceNo) {
        return Result.success(eventService.history(remittanceNo));
    }

    /**
     * 人工介入后重新推进清算。补偿重试达到上限会停止自动重发，
     * 收款账户冻结这类失败在人工解冻后可由此恢复。
     */
    @PostMapping("/{remittanceNo}/retry")
    @Operation(summary = "人工介入后重置重试计数并重新投递清算消息")
    public Result<RemittanceResponse> retrySettlement(@PathVariable String remittanceNo) {
        return Result.success(remittanceService.retrySettlement(remittanceNo));
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
