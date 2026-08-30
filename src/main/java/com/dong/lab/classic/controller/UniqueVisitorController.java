package com.dong.lab.classic.controller;

import com.dong.lab.classic.service.UniqueVisitorService;
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

/**
 * 独立访客统计。用 HyperLogLog，每个页面每天固定占用约 12KB，
 * 与访问量无关，代价是结果有约百分之零点八的误差。
 * 需要精确去重就不能用它，比如金额相关的统计。
 */
@RestController
@RequestMapping("/api/classic/uv")
@RequiredArgsConstructor
@Tag(name = "经典场景-独立访客")
public class UniqueVisitorController {

    private final UniqueVisitorService uniqueVisitorService;

    /**
     * 记录一次访问，返回当前估算的独立访客数。
     */
    @PostMapping("/record")
    @Operation(summary = "记录一次访问，返回估算的独立访客数")
    public Result<Long> record(@RequestParam(defaultValue = "home") String page,
                               @RequestParam String visitorId,
                               @RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(uniqueVisitorService.record(page, visitorId, date == null ? LocalDate.now() : date));
    }

    /**
     * 查询指定日期的独立访客数。
     */
    @GetMapping("/count")
    @Operation(summary = "查询指定日期的独立访客数")
    public Result<Long> count(@RequestParam(defaultValue = "home") String page,
                              @RequestParam(required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(uniqueVisitorService.count(page, date == null ? LocalDate.now() : date));
    }

    /**
     * 查询日期区间的独立访客数。合并多个 HyperLogLog 得到的去重结果，
     * 而不是简单相加，否则同一个人会被重复计数。
     */
    @GetMapping("/range")
    @Operation(summary = "查询日期区间的独立访客数，自动去重")
    public Result<Long> countBetween(@RequestParam(defaultValue = "home") String page,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.success(uniqueVisitorService.countBetween(page, from, to));
    }

}
