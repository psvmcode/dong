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

/**
 * 缓存实验室。提供可量化对比的实验入口，
 * 核心是穿透实验，其余接口用于观察多级缓存的运行状态。
 */
@RestController
@RequestMapping("/api/cache/lab")
@RequiredArgsConstructor
@Tag(name = "缓存实验室")
public class CacheLabController {

    private final CacheLabService cacheLabService;

    private final ProductService productService;

    private final MultiLevelCache multiLevelCache;

    private final CacheStats cacheStats;

    private final ObjectProvider<CaffeineCacheStore> l1Provider;

    private final ObjectProvider<RedisCacheStore> l2Provider;

    @GetMapping("/stats")
    @Operation(summary = "查看各层级缓存命中率")
    public Result<CacheStats.CacheStatsSnapshot> stats() {
        return Result.success(cacheStats.snapshot());
    }

    @PostMapping("/stats/reset")
    @Operation(summary = "重置缓存统计数据")
    public Result<Void> reset() {
        cacheStats.reset();
        return Result.success();
    }

    /**
     * 预热是使用布隆过滤器模式的前置条件，跳过会导致实验数据完全失真。
     */
    @PostMapping("/warm-up")
    @Operation(summary = "缓存预热，加载全部商品并填充布隆过滤器")
    public Result<Integer> warmUp() {
        return Result.success(productService.warmUp());
    }

    /**
     * 对照实验入口。guarded 为 true 走布隆过滤器，false 只靠空值标记。
     * 对比返回的 elapsedMillis 即可看出两种手段的差距。
     */
    @GetMapping("/penetration")
    @Operation(summary = "缓存穿透实验，对比空值标记与布隆过滤器两种防护手段")
    public Result<Map<String, Object>> penetration(@RequestParam(defaultValue = "2000") int count,
                                                   @RequestParam(defaultValue = "false") boolean guarded) {
        return Result.success(cacheLabService.penetration(count, guarded));
    }

    @GetMapping("/probe")
    @Operation(summary = "读取缓存，完整走一遍 L1 到 L2 再到回源的链路")
    public Result<String> probe(@RequestParam String key,
                                @RequestParam(defaultValue = "probe-value") String value) {
        return Result.success(multiLevelCache.get(key, String.class, Duration.ofMinutes(5), () -> value));
    }

    @DeleteMapping("/probe")
    @Operation(summary = "删除缓存并广播失效事件到其他节点")
    public Result<Void> evict(@RequestParam String key) {
        multiLevelCache.invalidate(key);
        return Result.success();
    }

    /**
     * 用 ObjectProvider 而不是直接注入，因为 L1 和 L2 都可以独立关闭，
     * 关闭时容器里没有对应 bean，直接注入会启动失败。
     */
    @GetMapping("/levels")
    @Operation(summary = "查看 L1 与 L2 的容量和命中明细")
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
