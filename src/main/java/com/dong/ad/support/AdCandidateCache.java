package com.dong.ad.support;

import com.dong.ad.entity.AdCreative;
import com.dong.ad.entity.AdUnit;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * 召回候选本地缓存。
 *
 * <p>广告请求是多读少写的：计划预算与单元定向一天改不了几次，
 * 但召回每秒可能几千次。每次都查三张表纯属浪费数据库。
 *
 * <p>一致性上采用「短 TTL + 写操作主动失效」：
 * 单元调价、定向变更、创意审核、计划状态迁移都会立即失效对应计划，
 * TTL 只是防止某条失效路径被遗漏后永久脏读。暂停投放后最多残留一个 TTL，
 * 这是性能与正确性之间的明确取舍，不是隐性缺陷。
 */
@Slf4j
@Component
public class AdCandidateCache {

    /**
     * 单元与创意的不可变快照。用 record 防止调用方拿到后修改缓存内容。
     *
     * @param units          启用单元列表
     * @param creativesByUnit 按单元 id 索引的一条已过审创意，召回排序只需要一条
     */
    public record Snapshot(List<AdUnit> units, Map<Long, AdCreative> creativesByUnit) {
    }

    /**
     * 全局快照键。召回本来就是一次查全部候选，按计划拆 key 会变成 N 次回源，
     * 反而比不缓存更慢。
     */
    private static final String GLOBAL_KEY = "global";

    /**
     * 缓存开关。
     */
    @Value("${dong.ad.candidate-cache-enabled:true}")
    private boolean enabled;

    /**
     * 兜底 TTL，单位秒。
     */
    @Value("${dong.ad.candidate-cache-ttl-seconds:30}")
    private long ttlSeconds;

    /**
     * 最大缓存计划数。
     */
    @Value("${dong.ad.candidate-cache-max-size:5000}")
    private long maxSize;

    /**
     * 底层缓存，@Value 注入完成后在 init 中构建。
     */
    private Cache<String, Snapshot> cache;

    /**
     * 命中数。
     */
    private final LongAdder hits = new LongAdder();

    /**
     * 未命中数。
     */
    private final LongAdder misses = new LongAdder();

    /**
     * 初始化缓存。
     */
    @PostConstruct
    public void init() {
        cache = Caffeine.newBuilder()
                .expireAfterWrite(Math.max(1L, ttlSeconds), TimeUnit.SECONDS)
                .maximumSize(Math.max(1L, maxSize))
                .build();
        log.info("ad candidate cache initialized ttl={}s maxSize={}", ttlSeconds, maxSize);
    }

    /**
     * 读取候选快照，未命中时调用加载器并回填。
     * 空结果不缓存，避免一条空窗被缓存到下一次配置变更。
     *
     * @param loader 回源加载器
     * @return 候选快照
     */
    public Snapshot get(Supplier<Snapshot> loader) {
        if (!enabled) {
            return loader.get();
        }
        Snapshot hit = cache.getIfPresent(GLOBAL_KEY);
        if (hit != null) {
            hits.increment();
            return hit;
        }
        misses.increment();
        Snapshot loaded = loader.get();
        if (loaded != null && !loaded.units().isEmpty()) {
            cache.put(GLOBAL_KEY, loaded);
        }
        return loaded;
    }

    /**
     * 主动失效候选快照。单元、创意、计划状态变更后必须调用，
     * 否则暂停投放最多要等一个 TTL 才生效。
     */
    public void invalidate() {
        cache.invalidate(GLOBAL_KEY);
    }

    /**
     * 生成缓存状态快照。
     *
     * @return 状态快照
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("enabled", enabled);
        snapshot.put("hits", hits.sum());
        snapshot.put("misses", misses.sum());
        snapshot.put("estimatedSize", cache.estimatedSize());
        return snapshot;
    }

}
