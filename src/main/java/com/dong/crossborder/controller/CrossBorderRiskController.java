package com.dong.crossborder.controller;

import com.dong.common.result.Result;
import com.dong.crossborder.dto.PayerAccountQueryRequest;
import com.dong.crossborder.dto.RouteQueryRequest;
import com.dong.crossborder.service.AmlMonitor;
import com.dong.crossborder.service.ChannelRouter;
import com.dong.crossborder.service.ComplianceService;
import com.dong.crossborder.service.FxExposureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 风控与路由。智能渠道路由回答"这笔汇款走哪个渠道最划算"，
 * AML 监控回答"这个付款人的行为模式是否可疑"，
 * 敞口监控回答"银行手里压了多少汇率风险"。
 * 三者共同构成跨境支付的风控闭环。
 */
@RestController
@RequestMapping("/api/crossborder/risk")
@RequiredArgsConstructor
@Validated
@Tag(name = "跨境支付-风控")
public class CrossBorderRiskController {

    /**
     * channelRouter。
     */
    private final ChannelRouter channelRouter;

    /**
     * amlMonitor。
     */
    private final AmlMonitor amlMonitor;

    /**
     * fxExposureService，业务服务层。
     */
    private final FxExposureService fxExposureService;

    /**
     * complianceService，业务服务层。
     */
    private final ComplianceService complianceService;

    /**
     * 查看指定金额下全部渠道的评分与推荐结果。
     */
    @PostMapping("/route")
    @Operation(summary = "渠道路由试算，返回各渠道评分与推荐渠道")
    public Result<Map<String, Object>> route(@Valid @RequestBody RouteQueryRequest request) {
        ChannelRouter.RouteDecision decision = channelRouter.route(request.getAmount(), request.isUrgent());
        return Result.success(Map.of("recommended", decision.channel(), "estimatedFee", decision.estimatedFee(), "reasons", decision.reasons(), "scores", channelRouter.scoreAll(request.getAmount(), request.isUrgent())));
    }

    /**
     * 查询某付款人的当日交易画像，含贴线笔数与金额明细。
     */
    @PostMapping("/aml/profile")
    @Operation(summary = "查询付款人当日交易画像")
    public Result<Map<String, Object>> amlProfile(@Valid @RequestBody PayerAccountQueryRequest request) {
        return Result.success(amlMonitor.structuringProfile(request.getPayerAccountId()));
    }

    /**
     * 命中拆分模式的账户列表。
     */
    @PostMapping("/aml/flagged")
    @Operation(summary = "查询命中拆分交易嫌疑的账户")
    public Result<List<Map<String, Object>>> flagged() {
        return Result.success(amlMonitor.flaggedAccounts());
    }

    /**
     * 清空监控数据，仅实验环境使用。
     */
    @DeleteMapping("/aml")
    @Operation(summary = "清空 AML 监控数据")
    public Result<Void> resetAml() {
        amlMonitor.reset();
        return Result.success();
    }

    /**
     * 重置某账户的日限额占用。运维场景使用：
     * 渠道侧确认某日计数有误时，重置后重新累计。
     */
    @PostMapping("/aml/reset-daily")
    @Operation(summary = "重置账户的日限额占用计数")
    public Result<Void> resetDaily(@RequestParam @Positive Long payerAccountId) {
        complianceService.resetDailyUsage(payerAccountId);
        return Result.success();
    }

    /**
     * 汇率敞口。按货币对展示未清算锁定量与浮动盈亏，超线提示平盘。
     */
    @PostMapping("/fx-exposure")
    @Operation(summary = "查询汇率敞口，含浮动盈亏与预警")
    public Result<Map<String, Object>> fxExposure() {
        return Result.success(fxExposureService.summary());
    }

}
