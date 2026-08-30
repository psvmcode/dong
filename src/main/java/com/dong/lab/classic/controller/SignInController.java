package com.dong.lab.classic.controller;

import com.dong.lab.classic.service.SignInService;
import com.dong.lab.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

/**
 * 用户签到。底层用 Bitmap 存储，每个用户每月只占几条到几十条记录，
 * 一年下来一个用户也就几百字节，这是 Bitmap 相比记录表的最大优势。
 */
@RestController
@RequestMapping("/api/classic/sign")
@RequiredArgsConstructor
@Tag(name = "经典场景-签到")
public class SignInController {

    private final SignInService signInService;

    /**
     * 签到。返回 false 表示当天已经签过，重复签到不会重复计数。
     */
    @PostMapping
    @Operation(summary = "签到，返回 false 表示当天已签过")
    public Result<Boolean> signIn(@RequestParam String userId,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(signInService.signIn(userId, date == null ? LocalDate.now() : date));
    }

    /**
     * 查询指定日期是否已签到。
     */
    @GetMapping
    @Operation(summary = "查询指定日期是否已签到")
    public Result<Boolean> hasSigned(@RequestParam String userId,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(signInService.hasSigned(userId, date == null ? LocalDate.now() : date));
    }

    /**
     * 连续签到天数，从指定日期往前推算，中断即止。
     */
    @GetMapping("/streak")
    @Operation(summary = "查询连续签到天数")
    public Result<Long> streak(@RequestParam String userId,
                               @RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(signInService.continuousDays(userId, date == null ? LocalDate.now() : date));
    }

    /**
     * 当月累计签到天数。
     */
    @GetMapping("/month")
    @Operation(summary = "查询当月累计签到天数")
    public Result<Long> monthCount(@RequestParam String userId,
                                   @RequestParam(required = false)
                                   @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return Result.success(signInService.countInMonth(userId, month == null ? YearMonth.now() : month));
    }

    /**
     * 当月签到日历，返回每一天是否签到的映射。
     */
    @GetMapping("/calendar")
    @Operation(summary = "查询当月签到日历")
    public Result<Map<String, Boolean>> calendar(@RequestParam String userId,
                                                 @RequestParam(required = false)
                                                 @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return Result.success(signInService.monthCalendar(userId, month == null ? YearMonth.now() : month));
    }

}
