package com.dong.lab.crossborder.controller;

import com.dong.lab.common.result.Result;
import com.dong.lab.crossborder.dto.FxQuoteResponse;
import com.dong.lab.crossborder.service.FxQuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
/**
 * 汇率报价。锁汇是跨境支付的核心概念：
 * 报价在有效期内按锁定汇率成交，过期自动失效需要重新询价。
 */
@RestController
@RequestMapping("/api/crossborder/fx")
@RequiredArgsConstructor
@Tag(name = "跨境支付-汇率")

public class CrossBorderFxController {

    /**
     * fxQuoteService，业务服务层。
     */
    private final FxQuoteService fxQuoteService;

    /**
     * 询价。返回买卖价与有效期，汇款时引用 quoteNo 即锁定该汇率。
     */
    @PostMapping("/quote")
    @Operation(summary = "询价，返回带有效期的汇率报价")
    public Result<FxQuoteResponse> quote(@RequestParam String sourceCurrency,
                                         @RequestParam String targetCurrency,
                                         @RequestParam(defaultValue = "300") long validSeconds) {
        return Result.success(fxQuoteService.quote(sourceCurrency, targetCurrency, validSeconds));
    }

    /**
     * 查询报价详情与剩余有效期。
     */
    @GetMapping("/{quoteNo}")
    @Operation(summary = "查询报价详情与剩余有效期")
    public Result<FxQuoteResponse> findByQuoteNo(@PathVariable String quoteNo) {
        return Result.success(fxQuoteService.findByQuoteNo(quoteNo));
    }

    /**
     * 查询某货币对的可用报价。
     */
    @GetMapping("/available")
    @Operation(summary = "查询某货币对的可用报价")
    public Result<List<FxQuoteResponse>> available(@RequestParam String sourceCurrency,
                                                   @RequestParam String targetCurrency) {
        return Result.success(fxQuoteService.available(sourceCurrency + "/" + targetCurrency));
    }

    /**
     * 当前中间价。高频读取，走 30 秒缓存。
     */
    @GetMapping("/rate")
    @Operation(summary = "查询当前中间价，走缓存")
    public Result<Map<String, Object>> currentRate(@RequestParam String sourceCurrency,
                                                   @RequestParam String targetCurrency) {
        BigDecimal rate = fxQuoteService.currentRate(sourceCurrency, targetCurrency);
        return Result.success(Map.of(
                "currencyPair", sourceCurrency + "/" + targetCurrency,
                "midRate", rate,
                "fee50kSwift", fxQuoteService.fee(new BigDecimal("50000"),
                        com.dong.lab.crossborder.enums.SettlementChannel.SWIFT),
                "fee50kCips", fxQuoteService.fee(new BigDecimal("50000"),
                        com.dong.lab.crossborder.enums.SettlementChannel.CIPS)));
    }

    /**
     * 手工触发过期报价清理。定时任务每分钟会自动执行一次。
     */
    @PostMapping("/expire")
    @Operation(summary = "手工清理过期报价")
    public Result<Integer> expireOverdue() {
        return Result.success(fxQuoteService.expireOverdue());
    }

}
