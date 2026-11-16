package com.dong.ad.support;

import com.dong.common.constant.Constants;
import com.dong.framework.redis.RedisService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * 广告请求限流。全局按 IP 的限流挡的是整体洪水，
 * 这一层按广告位挡「单个热点广告位把召回打穿」的情况。
 *
 * <p>分布式计数用 Redis 固定窗口；Redis 不可用时降级到本地秒级计数，
 * 精度下降但不会让限流这道防线整体消失。本地计数用有过期时间的缓存，
 * 不需要后台清理任务，也不会像永久 ConcurrentHashMap 那样慢慢膨胀。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdRateLimitSupport {

    private static final String KEY_PREFIX = "lab:ad:rl:";

    /**
     * 返回值就是窗口内累计请求数。
     */
    private static final String SCRIPT = """
            local current = redis.call('incr', KEYS[1])
            if current == 1 then
                redis.call('expire', KEYS[1], tonumber(ARGV[1]))
            end
            return current
            """;

    private static final RedisScript<Long> INCR = new DefaultRedisScript<>(SCRIPT, Long.class);

    /**
     * redisService，Redis 门面。
     */
    private final RedisService redisService;

    /**
     * 限流开关。
     */
    @Value("${dong.ad.request-rate-limit-enabled:true}")
    private boolean enabled;

    /**
     * 单广告位每秒允许的广告请求数。
     */
    @Value("${dong.ad.request-rate-limit-per-second:100}")
    private int limitPerSecond;

    /**
     * Redis 不可用时的本地计数缓存。
     */
    private Cache<String, LongAdder> localCounters;

    /**
     * 本地降级次数。
     */
    private final LongAdder localFallbacks = new LongAdder();

    /**
     * 被限流数。
     */
    private final LongAdder rejected = new LongAdder();

    /**
     * 初始化本地计数缓存。@Value 在构造之后才注入，不能放在字段初始化器里。
     */
    @PostConstruct
    public void init() {
        localCounters = Caffeine.newBuilder()
                .expireAfterWrite(2L, TimeUnit.SECONDS)
                .maximumSize(10_000)
                .build();
    }

    /**
     * 判断某广告位是否允许本次请求。
     *
     * @param placement 广告位标识
     * @return 是否放行
     */
    public boolean allow(String placement) {
        if (!enabled) {
            return true;
        }
        String key = KEY_PREFIX + normalize(placement) + Constants.SEPARATOR + System.currentTimeMillis() / 1000L;
        long current;
        try {
            Long result = redisService.execute(INCR, List.of(key), 2L);
            if (result == null) {
                throw new IllegalStateException("rate limit script returned nothing");
            }
            current = result;
        } catch (Exception ex) {
            localFallbacks.increment();
            current = localAllow(key);
        }
        if (current > Math.max(1, Math.min(limitPerSecond, Constants.MAX_BATCH_SIZE))) {
            rejected.increment();
            log.warn("ad request rate limited placement={} current={} limit={}", placement, current, limitPerSecond);
            return false;
        }
        return true;
    }

    /**
     * 生成限流状态快照。
     *
     * @return 状态快照
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("enabled", enabled);
        snapshot.put("limitPerSecond", limitPerSecond);
        snapshot.put("rejected", rejected.sum());
        snapshot.put("localFallbacks", localFallbacks.sum());
        return snapshot;
    }

    /**
     * 本地降级计数。LongAdder 的创建本身线程安全，但 computeIfAbsent 仍可能并发创建多个，
     * Caffeine 保证同一 key 只有一个值被发布。
     *
     * @param key 计数键
     * @return 当前计数
     */
    private long localAllow(String key) {
        LongAdder counter = localCounters.get(key, ignored -> new LongAdder());
        counter.increment();
        return counter.sum();
    }

    /**
     * 广告位标识归一。空值统一落到 _default，
     * 不归一化会让每个伪造 placement 都拿到独立配额，限流形同虚设。
     *
     * @param placement 原始标识
     * @return 归一化标识
     */
    private String normalize(String placement) {
        if (placement == null || placement.isBlank()) {
            return "_default";
        }
        String value = placement.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        return value.length() > Constants.MAX_NAME_LENGTH ? value.substring(0, Constants.MAX_NAME_LENGTH) : value;
    }

}
