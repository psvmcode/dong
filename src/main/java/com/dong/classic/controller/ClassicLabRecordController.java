package com.dong.classic.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.validation.annotation.Validated;
import com.dong.classic.entity.ClassicIdGenerated;
import com.dong.classic.entity.ClassicLockLabResult;
import com.dong.classic.entity.ClassicRateLimitLabResult;
import com.dong.classic.mapper.ClassicLabRecordMapper;
import com.dong.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
/**
 * 实验记录查询。发号器、锁、限流这三个场景是对照实验，
 * 结果落库后可以回看历史，不必每次重新跑一遍。
 */
@RestController
@Validated
@RequestMapping("/api/classic/lab-record")
@RequiredArgsConstructor
@Tag(name = "经典场景-实验记录")

public class ClassicLabRecordController {

    /**
     * 实验记录数据访问，MyBatis Mapper 数据访问层，只读查询直接使用。
     */
    private final ClassicLabRecordMapper labRecordMapper;

    /**
     * 查询某策略的历史发号记录，可对比耗时。
     */
    @GetMapping("/id")
    @Operation(summary = "查询某发号策略的历史生成记录")
    public Result<List<ClassicIdGenerated>> idGenerated(
            @RequestParam(defaultValue = "snowflake")
            @NotBlank @Size(max = 32) String strategy,
            @RequestParam(defaultValue = "10")
            @Min(1) @Max(Constants.MAX_QUERY_LIMIT) int limit) {
        return Result.success(labRecordMapper.selectIdGenerated(strategy, limit));
    }

    /**
     * 查询某模式的锁实验历史结果。
     */
    @GetMapping("/lock")
    @Operation(summary = "查询锁实验的历史结果")
    public Result<List<ClassicLockLabResult>> lockLab(
            @RequestParam(defaultValue = "no-lock")
            @NotBlank @Size(max = 32) String mode,
            @RequestParam(defaultValue = "10")
            @Min(1) @Max(Constants.MAX_QUERY_LIMIT) int limit) {
        return Result.success(labRecordMapper.selectLockLabResult(mode, limit));
    }

    /**
     * 查询某业务键的限流对比历史，重点关注第二轮放行数。
     */
    @GetMapping("/limiter")
    @Operation(summary = "查询限流算法对比的历史结果")
    public Result<List<ClassicRateLimitLabResult>> rateLimit(
            @RequestParam(defaultValue = "demo")
            @NotBlank @Size(max = 128) String bizKey,
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(Constants.MAX_QUERY_LIMIT) int limit) {
        return Result.success(labRecordMapper.selectRateLimitResult(bizKey, limit));
    }

}
