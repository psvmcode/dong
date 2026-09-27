package com.dong.classic.controller;

import com.dong.classic.dto.ConcurrencyLabQueryRequest;
import com.dong.classic.dto.DelayQueueTakeQueryRequest;
import com.dong.classic.dto.GeoDistanceQueryRequest;
import com.dong.classic.dto.GeoNearbyQueryRequest;
import com.dong.classic.dto.IdGenerateQueryRequest;
import com.dong.classic.dto.LimiterCompareQueryRequest;
import com.dong.classic.dto.LimiterTryQueryRequest;
import com.dong.classic.dto.NearbyPlaceResponse;
import com.dong.classic.service.DelayQueueService;
import com.dong.classic.service.GeoService;
import com.dong.classic.service.IdGeneratorService;
import com.dong.classic.service.LockLabService;
import com.dong.classic.service.RateLimitLabService;
import com.dong.common.constant.Constants;
import com.dong.common.result.Result;
import com.dong.framework.limiter.RateLimitManager;
import com.dong.framework.limiter.RateLimitRule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Redis 经典场景集合，包括延迟队列、地理位置、发号器、分布式锁与限流。
 * 锁和限流两组接口都是对照实验，用来量化"加了会怎样、不加会怎样"。
 */
@RestController
@RequestMapping("/api/classic")
@RequiredArgsConstructor
@Validated
@Tag(name = "经典场景-Redis")
public class RedisLabController {

    /**
     * 延迟队列服务。
     */
    private final DelayQueueService delayQueueService;

    /**
     * 地理位置服务。
     */
    private final GeoService geoService;

    /**
     * 发号器服务。
     */
    private final IdGeneratorService idGeneratorService;

    /**
     * 分布式锁实验服务。
     */
    private final LockLabService lockLabService;

    /**
     * 限流算法对比服务。
     */
    private final RateLimitLabService rateLimitLabService;

    /**
     * 限流管理器。
     */
    private final RateLimitManager rateLimitManager;

    /**
     * 投递延迟任务。任务在延迟时间过后才可被取出，
     * 适合做超时未支付自动关单这类场景。
     */
    @PostMapping("/delay-queue/offer")
    @Operation(summary = "投递延迟任务，到达指定时间后才可被消费")
    public Result<Void> offer(@RequestParam @NotBlank @Size(max = Constants.MAX_TEXT_LENGTH) String payload,
                              @RequestParam(defaultValue = "5")
                              @Min(0) @Max(Constants.MAX_DELAY_SECONDS) long delaySeconds) {
        delayQueueService.offer(payload, Duration.ofSeconds(delaySeconds));
        return Result.success();
    }

    /**
     * 取出已到期任务。未到期的不会被返回。
     */
    @PostMapping("/delay-queue/take")
    @Operation(summary = "取出已到期的延迟任务")
    public Result<List<String>> take(@Valid @RequestBody DelayQueueTakeQueryRequest request) {
        return Result.success(delayQueueService.take(request.getLimit()));
    }

    /**
     * 队列中待消费的任务数。
     */
    @PostMapping("/delay-queue/size")
    @Operation(summary = "查询延迟队列的待消费数量")
    public Result<Long> delayQueueSize() {
        return Result.success(delayQueueService.size());
    }

    /**
     * 添加地理位置坐标。
     */
    @PostMapping("/geo")
    @Operation(summary = "添加地理位置坐标")
    public Result<Long> geoAdd(@RequestParam(defaultValue = "beijing")
                               @NotBlank @Size(max = Constants.MAX_NAME_LENGTH) String city,
                               @RequestParam @Min(-180) @Max(180) double longitude,
                               @RequestParam @Min(-90) @Max(90) double latitude,
                               @RequestParam @NotBlank @Size(max = Constants.MAX_NAME_LENGTH) String member) {
        return Result.success(geoService.add(city, longitude, latitude, member));
    }

    /**
     * 查询指定坐标附近范围内的成员。底层是 Redis GEO，
     * 本质上是把经纬度编码进 ZSet 再做范围查询。
     */
    @PostMapping("/geo/nearby")
    @Operation(summary = "查询指定坐标附近范围内的成员")
    public Result<List<NearbyPlaceResponse>> nearby(@Valid @RequestBody GeoNearbyQueryRequest request) {
        return Result.success(geoService.nearby(request.getCity(), request.getLongitude(), request.getLatitude(), request.getRadiusKm(), request.getLimit()));
    }

    /**
     * 计算两个成员之间的距离。
     */
    @PostMapping("/geo/distance")
    @Operation(summary = "计算两个成员之间的距离")
    public Result<Double> distance(@Valid @RequestBody GeoDistanceQueryRequest request) {
        return Result.success(geoService.distance(request.getCity(), request.getFirst(), request.getSecond()));
    }

    /**
     * 按指定策略批量生成 id。四种策略各有取舍：
     * 雪花算法趋势递增但依赖机器时钟，号段模式对数据库有压力但绝对递增，
     * INCR 最简单但会暴露业务量，UUID 无序不适合做数据库主键。
     */
    @PostMapping("/id")
    @Operation(summary = "按指定策略批量生成 id，并给出耗时")
    public Result<Map<String, Object>> generateId(@Valid @RequestBody IdGenerateQueryRequest request) {
        return Result.success(idGeneratorService.generate(request.getStrategy(), request.getCount()));
    }

    /**
     * 不加锁的并发自增，用作对照组。结果会大量丢失更新。
     */
    @PostMapping("/lock/without-lock")
    @Operation(summary = "不加锁的并发自增，用作对照组，会丢失更新")
    public Result<Map<String, Object>> withoutLock(@Valid @RequestBody ConcurrencyLabQueryRequest request) {
        return Result.success(lockLabService.withoutLock(request.getThreads(), request.getLoops()));
    }

    /**
     * 加锁的并发自增。结果与期望值完全一致，
     * 代价是耗时比不加锁高出一到两个数量级，这就是正确性的成本。
     */
    @PostMapping("/lock/with-lock")
    @Operation(summary = "加 Redisson 锁的并发自增，结果精确但耗时高得多")
    public Result<Map<String, Object>> withLock(@Valid @RequestBody ConcurrencyLabQueryRequest request) {
        return Result.success(lockLabService.withLock(request.getThreads(), request.getLoops()));
    }

    /**
     * 用指定算法尝试获取一次配额。
     */
    @PostMapping("/limiter/try")
    @Operation(summary = "用指定算法尝试获取一次配额")
    public Result<Boolean> tryAcquire(@Valid @RequestBody LimiterTryQueryRequest request) {
        RateLimitRule rule = new RateLimitRule(request.getLimit(), Duration.ofSeconds(request.getWindowSeconds()), request.getAlgorithm());
        return Result.success(rateLimitManager.tryAcquire(request.getKey(), rule, request.isDistributed()));
    }

    /**
     * 四种限流算法的对比。
     *
     * <p>只打一轮突发时四种算法的放行数量必然相同，因为窗口内都最多放行 limit 个，
     * 区分不出算法。真正的差异在配额如何恢复，要看第二轮。
     *
     * <p>传入 gapMillis 后会在两轮突发之间等待，此时四种算法表现不同：
     * 固定窗口只有跨过窗口边界才放行，滑动窗口放行滑出窗口的那部分，
     * 令牌桶放行补充的令牌，漏桶放行漏出的水量。
     * 建议配合较短的窗口观察，例如 limit=10、windowSeconds=6、gapMillis=3000。
     */
    @PostMapping("/limiter/compare")
    @Operation(summary = "对比固定窗口、滑动窗口、令牌桶、漏桶四种算法，可指定两轮突发间隔")
    public Result<Map<String, Object>> compare(@Valid @RequestBody LimiterCompareQueryRequest request) {
        return Result.success(rateLimitLabService.compare(request.getBizKey(), request.getLimit(), request.getWindowSeconds(), request.getAttempts(), request.isDistributed(), request.getGapMillis()));
    }

}
