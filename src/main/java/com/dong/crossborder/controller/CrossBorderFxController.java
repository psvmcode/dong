package com.dong.crossborder.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.dong.common.result.Result;
import com.dong.crossborder.dto.FxQuoteResponse;
import com.dong.crossborder.dto.FxRateResponse;
import com.dong.crossborder.service.FxQuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
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
@Validated
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
    public Result<FxQuoteResponse> quote(@RequestParam @Pattern(regexp = "^[A-Z]{3}$") String sourceCurrency,
                                         @RequestParam @Pattern(regexp = "^[A-Z]{3}$") String targetCurrency,
                                         @RequestParam(defaultValue = "300") @Min(1) @Max(86400) long validSeconds) {
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
    public Result<List<FxQuoteResponse>> available(@RequestParam
 @NotBlank @Size(max = 128) String sourceCurrency,
                                                   @RequestParam
 @NotBlank @Size(max = 128) String targetCurrency) {
        return Result.success(fxQuoteService.available(sourceCurrency + "/" + targetCurrency));
    }

    /**
     * 当前中间价。高频读取，走 30 秒缓存。
     */
    @GetMapping("/rate")
    @Operation(summary = "查询当前中间价，走缓存")
    public Result<Map<String, Object>> currentRate(@RequestParam
 @NotBlank @Size(max = 128) String sourceCurrency,
                                                   @RequestParam
 @NotBlank @Size(max = 128) String targetCurrency) {
        BigDecimal rate = fxQuoteService.currentRate(sourceCurrency, targetCurrency);
        return Result.success(Map.of(
                "currencyPair", sourceCurrency + "/" + targetCurrency,
                "midRate", rate,
                "fee50kSwift", fxQuoteService.fee(new BigDecimal("50000"),
                        com.dong.crossborder.enums.SettlementChannel.SWIFT),
                "fee50kCips", fxQuoteService.fee(new BigDecimal("50000"),
                        com.dong.crossborder.enums.SettlementChannel.CIPS)));
    }

    /**
     * 查询全部牌价。牌价落库后才能回答「这笔成交时用的是什么价」。
     */
    @GetMapping("/rates")
    @Operation(summary = "查询全部币种牌价")
    public Result<List<FxRateResponse>> rates() {
        return Result.success(fxQuoteService.allRates());
    }

    /**
     * 调整牌价。真实系统由交易系统推送，这里提供手动入口，
     * 调整后会失效中间价缓存，避免询价仍在用旧价。
     */
    @PostMapping("/rate")
    @Operation(summary = "调整某个币种的牌价")
    public Result<Void> updateRate(@RequestParam @Pattern(regexp = "^[A-Z]{3}$") String currency,
                                   @RequestParam @DecimalMin(value = "0", inclusive = false)
                                   @Digits(integer = 12, fraction = 8) BigDecimal usdRate) {
        fxQuoteService.updateRate(currency, usdRate);
        return Result.success();
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
