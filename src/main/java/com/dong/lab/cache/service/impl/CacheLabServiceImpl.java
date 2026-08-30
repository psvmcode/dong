package com.dong.lab.cache.service.impl;

import com.dong.lab.cache.service.CacheLabService;
import com.dong.lab.cache.service.ProductService;
import com.dong.lab.common.exception.BusinessException;
import com.dong.lab.framework.cache.CacheStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheLabServiceImpl implements CacheLabService {

    private final ProductService productService;

    private final CacheStats cacheStats;

    /**
     * 用不存在的 id 打量请求，对比两种防穿透手段的耗时与拦截效果。
     * id 取 9000000 到 9999999 之间，这个区间在数据库中一定不存在。
     */
    @Override
    public Map<String, Object> penetration(int count, boolean guarded) {
        long before = cacheStats.snapshot().penetrationBlocked();
        long start = System.currentTimeMillis();
        int rejected = 0;
        for (int i = 0; i < count; i++) {
            long id = ThreadLocalRandom.current().nextLong(9_000_000L, 9_999_999L);
            try {
                if (guarded) {
                    productService.findByIdGuarded(id);
                } else {
                    productService.findById(id);
                }
            } catch (BusinessException ex) {
                rejected++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requests", count);
        result.put("rejectedOrAbsent", rejected);
        result.put("elapsedMillis", System.currentTimeMillis() - start);
        result.put("penetrationBlockedDelta", cacheStats.snapshot().penetrationBlocked() - before);
        result.put("mode", guarded ? "bloom-filter" : "empty-marker");
        log.info("penetration test mode={} count={} elapsed={}ms",
                result.get("mode"), count, result.get("elapsedMillis"));
        return result;
    }

}
