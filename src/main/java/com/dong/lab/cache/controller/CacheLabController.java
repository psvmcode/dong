package com.dong.lab.cache.controller;

import com.dong.lab.cache.service.CacheLabService;
import com.dong.lab.cache.service.ProductService;
import com.dong.lab.common.result.Result;
import com.dong.lab.framework.cache.CacheStats;
import com.dong.lab.framework.cache.CaffeineCacheStore;
import com.dong.lab.framework.cache.MultiLevelCache;
import com.dong.lab.framework.cache.RedisCacheStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/cache/lab")
@RequiredArgsConstructor
@Tag(name = "cache-lab")
public class CacheLabController {

    private final CacheLabService cacheLabService;

    private final ProductService productService;

    private final MultiLevelCache multiLevelCache;

    private final CacheStats cacheStats;

    private final ObjectProvider<CaffeineCacheStore> l1Provider;

    private final ObjectProvider<RedisCacheStore> l2Provider;

    @GetMapping("/stats")
    @Operation(summary = "hit ratio per level")
    public Result<CacheStats.CacheStatsSnapshot> stats() {
        return Result.success(cacheStats.snapshot());
    }

    @PostMapping("/stats/reset")
    public Result<Void> reset() {
        cacheStats.reset();
        return Result.success();
    }

    @PostMapping("/warm-up")
    public Result<Integer> warmUp() {
        return Result.success(productService.warmUp());
    }

    @GetMapping("/penetration")
    @Operation(summary = "burst of ids that do not exist, with or without the bloom filter")
    public Result<Map<String, Object>> penetration(@RequestParam(defaultValue = "2000") int count,
                                                   @RequestParam(defaultValue = "false") boolean guarded) {
        return Result.success(cacheLabService.penetration(count, guarded));
    }

    @GetMapping("/probe")
    public Result<String> probe(@RequestParam String key,
                                @RequestParam(defaultValue = "probe-value") String value) {
        return Result.success(multiLevelCache.get(key, String.class, Duration.ofMinutes(5), () -> value));
    }

    @DeleteMapping("/probe")
    public Result<Void> evict(@RequestParam String key) {
        multiLevelCache.invalidate(key);
        return Result.success();
    }

    @GetMapping("/levels")
    public Result<Map<String, Object>> levels() {
        Map<String, Object> result = new LinkedHashMap<>();
        l1Provider.ifAvailable(store -> {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("size", store.estimatedSize());
            detail.put("hitCount", store.snapshot().hitCount());
            detail.put("missCount", store.snapshot().missCount());
            detail.put("evictionCount", store.snapshot().evictionCount());
            result.put("l1-" + store.name(), detail);
        });
        l2Provider.ifAvailable(store -> {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("size", store.estimatedSize());
            result.put("l2-" + store.name(), detail);
        });
        return Result.success(result);
    }

}
