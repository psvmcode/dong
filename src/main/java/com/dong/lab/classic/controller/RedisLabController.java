package com.dong.lab.classic.controller;

import com.dong.lab.classic.dto.NearbyPlaceResponse;
import com.dong.lab.classic.service.DelayQueueService;
import com.dong.lab.classic.service.GeoService;
import com.dong.lab.classic.service.IdGeneratorService;
import com.dong.lab.classic.service.LockLabService;
import com.dong.lab.classic.service.RateLimitLabService;
import com.dong.lab.common.result.Result;
import com.dong.lab.framework.limiter.RateLimitAlgorithm;
import com.dong.lab.framework.limiter.RateLimitManager;
import com.dong.lab.framework.limiter.RateLimitRule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
@Tag(name = "经典场景-Redis")

public class RedisLabController {

    /**
     * delayQueueService，业务服务层。
     */
    private final DelayQueueService delayQueueService;

    /**
     * geoService，业务服务层。
     */
    private final GeoService geoService;

    /**
     * idGeneratorService，业务服务层。
     */
    private final IdGeneratorService idGeneratorService;

    /**
     * lockLabService，业务服务层。
     */
    private final LockLabService lockLabService;

    /**
     * rateLimitLabService，业务服务层。
     */
    private final RateLimitLabService rateLimitLabService;

    /**
     * rateLimitManager。
     */
    private final RateLimitManager rateLimitManager;

    /**
     * 投递延迟任务。任务在延迟时间过后才可被取出，
     * 适合做超时未支付自动关单这类场景。
     */
    @PostMapping("/delay-queue/offer")
    @Operation(summary = "投递延迟任务，到达指定时间后才可被消费")
    public Result<Void> offer(@RequestParam String payload,
                              @RequestParam(defaultValue = "5") long delaySeconds) {
        delayQueueService.offer(payload, Duration.ofSeconds(delaySeconds));
        return Result.success();
    }

    /**
     * 取出已到期任务。未到期的不会被返回。
     */
    @GetMapping("/delay-queue/take")
    @Operation(summary = "取出已到期的延迟任务")
    public Result<List<String>> take(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(delayQueueService.take(limit));
    }

    /**
     * 队列中待消费的任务数。
     */
    @GetMapping("/delay-queue/size")
    @Operation(summary = "查询延迟队列的待消费数量")
    public Result<Long> delayQueueSize() {
        return Result.success(delayQueueService.size());
    }

    /**
     * 添加地理位置坐标。
     */
    @PostMapping("/geo")
    @Operation(summary = "添加地理位置坐标")
    public Result<Long> geoAdd(@RequestParam(defaultValue = "beijing") String city,
                               @RequestParam double longitude,
                               @RequestParam double latitude,
                               @RequestParam String member) {
        return Result.success(geoService.add(city, longitude, latitude, member));
    }

    /**
     * 查询指定坐标附近范围内的成员。底层是 Redis GEO，
     * 本质上是把经纬度编码进 ZSet 再做范围查询。
     */
    @GetMapping("/geo/nearby")
    @Operation(summary = "查询指定坐标附近范围内的成员")
    public Result<List<NearbyPlaceResponse>> nearby(@RequestParam(defaultValue = "beijing") String city,
                                                    @RequestParam double longitude,
                                                    @RequestParam double latitude,
                                                    @RequestParam(defaultValue = "5") double radiusKm,
                                                    @RequestParam(defaultValue = "10") int limit) {
        return Result.success(geoService.nearby(city, longitude, latitude, radiusKm, limit));
    }

    /**
     * 计算两个成员之间的距离。
     */
    @GetMapping("/geo/distance")
    @Operation(summary = "计算两个成员之间的距离")
    public Result<Double> distance(@RequestParam(defaultValue = "beijing") String city,
                                   @RequestParam String first,
                                   @RequestParam String second) {
        return Result.success(geoService.distance(city, first, second));
    }

    /**
     * 按指定策略批量生成 id。四种策略各有取舍：
     * 雪花算法趋势递增但依赖机器时钟，号段模式对数据库有压力但绝对递增，
     * INCR 最简单但会暴露业务量，UUID 无序不适合做数据库主键。
     */
    @GetMapping("/id")
    @Operation(summary = "按指定策略批量生成 id，并给出耗时")
    public Result<Map<String, Object>> generateId(@RequestParam(defaultValue = "snowflake") String strategy,
                                                  @RequestParam(defaultValue = "1000") int count) {
        return Result.success(idGeneratorService.generate(strategy, count));
    }

    /**
     * 不加锁的并发自增，用作对照组。结果会大量丢失更新。
     */
    @GetMapping("/lock/without-lock")
    @Operation(summary = "不加锁的并发自增，用作对照组，会丢失更新")
    public Result<Map<String, Object>> withoutLock(@RequestParam(defaultValue = "16") int threads,
                                                   @RequestParam(defaultValue = "20") int loops) {
        return Result.success(lockLabService.withoutLock(threads, loops));
    }

    /**
     * 加锁的并发自增。结果与期望值完全一致，
     * 代价是耗时比不加锁高出一到两个数量级，这就是正确性的成本。
     */
    @GetMapping("/lock/with-lock")
    @Operation(summary = "加 Redisson 锁的并发自增，结果精确但耗时高得多")
    public Result<Map<String, Object>> withLock(@RequestParam(defaultValue = "16") int threads,
                                                @RequestParam(defaultValue = "20") int loops) {
        return Result.success(lockLabService.withLock(threads, loops));
    }

    /**
     * 用指定算法尝试获取一次配额。
     */
    @GetMapping("/limiter/try")
    @Operation(summary = "用指定算法尝试获取一次配额")
    public Result<Boolean> tryAcquire(@RequestParam(defaultValue = "demo") String key,
                                      @RequestParam(defaultValue = "TOKEN_BUCKET") RateLimitAlgorithm algorithm,
                                      @RequestParam(defaultValue = "10") long limit,
                                      @RequestParam(defaultValue = "60") long windowSeconds,
                                      @RequestParam(defaultValue = "true") boolean distributed) {
        RateLimitRule rule = new RateLimitRule(limit, Duration.ofSeconds(windowSeconds), algorithm);
        return Result.success(rateLimitManager.tryAcquire(key, rule, distributed));
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
    @GetMapping("/limiter/compare")
    @Operation(summary = "对比固定窗口、滑动窗口、令牌桶、漏桶四种算法，可指定两轮突发间隔")
    public Result<Map<String, Object>> compare(@RequestParam(defaultValue = "demo") String bizKey,
                                               @RequestParam(defaultValue = "10") long limit,
                                               @RequestParam(defaultValue = "60") long windowSeconds,
                                               @RequestParam(defaultValue = "50") int attempts,
                                               @RequestParam(defaultValue = "true") boolean distributed,
                                               @RequestParam(defaultValue = "0") long gapMillis) {
        return Result.success(rateLimitLabService.compare(bizKey, limit, windowSeconds, attempts, distributed, gapMillis));
    }

}
