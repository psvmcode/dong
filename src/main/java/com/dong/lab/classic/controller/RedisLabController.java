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

@RestController
@RequestMapping("/api/classic")
@RequiredArgsConstructor
@Tag(name = "classic-lab")
public class RedisLabController {

    private final DelayQueueService delayQueueService;

    private final GeoService geoService;

    private final IdGeneratorService idGeneratorService;

    private final LockLabService lockLabService;

    private final RateLimitLabService rateLimitLabService;

    private final RateLimitManager rateLimitManager;

    @PostMapping("/delay-queue/offer")
    public Result<Void> offer(@RequestParam String payload,
                              @RequestParam(defaultValue = "5") long delaySeconds) {
        delayQueueService.offer(payload, Duration.ofSeconds(delaySeconds));
        return Result.success();
    }

    @GetMapping("/delay-queue/take")
    public Result<List<String>> take(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(delayQueueService.take(limit));
    }

    @GetMapping("/delay-queue/size")
    public Result<Long> delayQueueSize() {
        return Result.success(delayQueueService.size());
    }

    @PostMapping("/geo")
    public Result<Long> geoAdd(@RequestParam(defaultValue = "beijing") String city,
                               @RequestParam double longitude,
                               @RequestParam double latitude,
                               @RequestParam String member) {
        return Result.success(geoService.add(city, longitude, latitude, member));
    }

    @GetMapping("/geo/nearby")
    public Result<List<NearbyPlaceResponse>> nearby(@RequestParam(defaultValue = "beijing") String city,
                                                    @RequestParam double longitude,
                                                    @RequestParam double latitude,
                                                    @RequestParam(defaultValue = "5") double radiusKm,
                                                    @RequestParam(defaultValue = "10") int limit) {
        return Result.success(geoService.nearby(city, longitude, latitude, radiusKm, limit));
    }

    @GetMapping("/geo/distance")
    public Result<Double> distance(@RequestParam(defaultValue = "beijing") String city,
                                   @RequestParam String first,
                                   @RequestParam String second) {
        return Result.success(geoService.distance(city, first, second));
    }

    @GetMapping("/id")
    public Result<Map<String, Object>> generateId(@RequestParam(defaultValue = "snowflake") String strategy,
                                                  @RequestParam(defaultValue = "1000") int count) {
        return Result.success(idGeneratorService.generate(strategy, count));
    }

    @GetMapping("/lock/without-lock")
    @Operation(summary = "concurrent increments without a lock, some of them get lost")
    public Result<Map<String, Object>> withoutLock(@RequestParam(defaultValue = "16") int threads,
                                                   @RequestParam(defaultValue = "20") int loops) {
        return Result.success(lockLabService.withoutLock(threads, loops));
    }

    @GetMapping("/lock/with-lock")
    @Operation(summary = "same workload inside a redisson lock, the total is exact")
    public Result<Map<String, Object>> withLock(@RequestParam(defaultValue = "16") int threads,
                                                @RequestParam(defaultValue = "20") int loops) {
        return Result.success(lockLabService.withLock(threads, loops));
    }

    @GetMapping("/limiter/try")
    public Result<Boolean> tryAcquire(@RequestParam(defaultValue = "demo") String key,
                                      @RequestParam(defaultValue = "TOKEN_BUCKET") RateLimitAlgorithm algorithm,
                                      @RequestParam(defaultValue = "10") long limit,
                                      @RequestParam(defaultValue = "60") long windowSeconds,
                                      @RequestParam(defaultValue = "true") boolean distributed) {
        RateLimitRule rule = new RateLimitRule(limit, Duration.ofSeconds(windowSeconds), algorithm);
        return Result.success(rateLimitManager.tryAcquire(key, rule, distributed));
    }

    @GetMapping("/limiter/compare")
    public Result<Map<String, Object>> compare(@RequestParam(defaultValue = "demo") String bizKey,
                                               @RequestParam(defaultValue = "10") long limit,
                                               @RequestParam(defaultValue = "60") long windowSeconds,
                                               @RequestParam(defaultValue = "50") int attempts,
                                               @RequestParam(defaultValue = "true") boolean distributed) {
        return Result.success(rateLimitLabService.compare(bizKey, limit, windowSeconds, attempts, distributed));
    }

}
