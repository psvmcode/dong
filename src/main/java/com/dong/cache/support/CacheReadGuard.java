package com.dong.cache.support;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.framework.limiter.RateLimitAlgorithm;
import com.dong.framework.limiter.RateLimitManager;
import com.dong.framework.limiter.RateLimitRule;
import com.dong.framework.redis.RedisService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 缓存读接口的防护。三件事，挡的是三种不同的攻击：
 * <ul>
 *     <li>按 IP 的读限流：挡高频爬取，配额比全局限流更紧</li>
 *     <li>重接口单独配额：预热、穿透实验、全量列表这类接口一次就能拖垮服务</li>
 *     <li>扫描式穿透检测：连续查到不存在的数据，说明对方在撞 id，
 *         直接短期封禁，这是缓存场景特有的防护</li>
 * </ul>
 *
 * <p>所有状态优先存 Redis 以支持多节点，但 Redis 不可用时全部降级到本地计数。
 * 这是硬要求：防护组件不能依赖它正在保护的那个中间件，
 * 否则 Redis 一出故障，限流先失效，压力反而完整地灌进数据库。
 *
 * <p>限流器自身故障一律放行。拒绝请求是把「中间件抖动」放大成「业务不可用」，
 * 而放行之后还有布隆过滤器、空值标记、回源频控三道防线接着。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheReadGuard {

    private static final String LIMIT_PREFIX = "lab:cache:guard:rl:";
    private static final String MISS_PREFIX = "lab:cache:guard:miss:";
    private static final String BLOCK_PREFIX = "lab:cache:guard:block:";

    // 空闲 key 十分钟后清理，避免长期不访问的 IP 白占内存
    private static final Duration LOCAL_IDLE_EVICTION = Duration.ofMinutes(10);

    private static final long LOCAL_MAX_KEYS = 100_000L;

    // 放行表的有效期，见 localClear 的说明
    private static final long CLEAR_WINDOW_MILLIS = 10_000L;

    /**
     * 限流管理器。
     */
    private final RateLimitManager rateLimitManager;

    /**
     * Redis 服务，用于跨节点共享封禁状态。
     */
    private final RedisService redisService;

    /**
     * 是否开启缓存读防护。
     */
    @Value("${dong.cache.guard-enabled:true}")
    private boolean enabled;

    /**
     * 单个 IP 每分钟允许的普通读次数。
     */
    @Value("${dong.cache.guard-read-limit-per-minute:600}")
    private long readLimitPerMinute;

    /**
     * 单个 IP 每分钟允许的重量级操作次数。
     */
    @Value("${dong.cache.guard-heavy-limit-per-minute:20}")
    private long heavyLimitPerMinute;

    /**
     * 窗口内累计多少次「查不到」判定为扫描。
     */
    @Value("${dong.cache.guard-scan-miss-threshold:30}")
    private long scanMissThreshold;

    /**
     * 扫描检测的统计窗口，单位秒。
     */
    @Value("${dong.cache.guard-scan-window-seconds:60}")
    private long scanWindowSeconds;

    /**
     * 判定为扫描后的封禁时长，单位秒。
     */
    @Value("${dong.cache.guard-block-seconds:60}")
    private long blockSeconds;

    /**
     * Redis 不可用时的本地未命中计数。
     */
    private final Cache<String, MissCounter> localMiss = Caffeine.newBuilder().expireAfterAccess(LOCAL_IDLE_EVICTION).maximumSize(LOCAL_MAX_KEYS).build();

    /**
     * Redis 不可用时的本地封禁表，值为解封时间戳。
     */
    private final Cache<String, Long> localBlock = Caffeine.newBuilder().expireAfterAccess(LOCAL_IDLE_EVICTION).maximumSize(LOCAL_MAX_KEYS).build();

    /**
     * 本地放行表，值为免查 Redis 的截止时间戳。
     *
     * <p>封禁检查挂在缓存模块的每一个读请求上，而绝大多数请求根本没被封禁，
     * 每次都为它们发一次 EXISTS 是纯空转。这里记住「最近确认过未被封禁」的 IP，
     * 短时间内不再回查 Redis。窗口取十秒：被封禁的判定最多晚十秒生效，
     * 而封禁本身就是六十秒级的惩罚，这点延迟不影响防护效果。
     */
    private final Cache<String, Long> localClear = Caffeine.newBuilder().expireAfterAccess(LOCAL_IDLE_EVICTION).maximumSize(LOCAL_MAX_KEYS).build();

    /**
     * 普通读限流。令牌桶允许一定突发，正常浏览不会被误伤。
     *
     * @param clientIp 客户端 IP
     */
    public void checkRead(String clientIp) {
        acquire(clientIp, "read", readLimitPerMinute, RateLimitAlgorithm.TOKEN_BUCKET);
    }

    /**
     * 重量级操作限流。用固定窗口而不是令牌桶，
     * 因为这类接口每次调用的成本都很高，不该允许突发。
     *
     * @param clientIp 客户端 IP
     */
    public void checkHeavy(String clientIp) {
        acquire(clientIp, "heavy", heavyLimitPerMinute, RateLimitAlgorithm.FIXED_WINDOW);
    }

    /**
     * 检查该 IP 是否已被判定为扫描而封禁。
     *
     * @param clientIp 客户端 IP
     */
    public void checkNotBlocked(String clientIp) {
        if (!enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        Long clearUntil = localClear.getIfPresent(clientIp);
        if (clearUntil != null && clearUntil > now) {
            return;
        }
        try {
            if (redisService.hasKey(BLOCK_PREFIX + clientIp)) {
                throw blocked(clientIp);
            }
            localClear.put(clientIp, now + CLEAR_WINDOW_MILLIS);
            return;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("cache guard block check degraded to local: {}", ex.getMessage());
        }
        Long until = localBlock.getIfPresent(clientIp);
        if (until != null && until > now) {
            throw blocked(clientIp);
        }
    }

    /**
     * 记录一次「查不到」。累计到阈值就判定为撞 id 并短期封禁。
     *
     * @param clientIp 客户端 IP
     */
    public void recordMiss(String clientIp) {
        if (!enabled) {
            return;
        }
        long count = incrementMiss(clientIp);
        if (count >= scanMissThreshold) {
            block(clientIp);
            log.warn("suspected cache key scan from ip={} missCount={} blockedSeconds={}", clientIp, count, blockSeconds);
        }
    }

    /**
     * 获取配额。限流器自身异常时放行，不把中间件故障放大成业务不可用。
     */
    private void acquire(String clientIp, String scene, long limit, RateLimitAlgorithm algorithm) {
        if (!enabled || limit <= 0) {
            return;
        }
        try {
            RateLimitRule rule = new RateLimitRule(limit, Duration.ofMinutes(1), algorithm);
            if (!rateLimitManager.tryAcquire(LIMIT_PREFIX + scene + ":" + clientIp, rule, false)) {
                throw new BusinessException(Constants.CODE_TOO_MANY_REQUESTS,
                        "cache " + scene + " requests too frequent, at most " + limit + " per minute");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("cache guard limiter unavailable, letting the request through: {}", ex.getMessage());
        }
    }

    /**
     * 累加未命中次数，优先用 Redis 以便多节点共享。
     *
     * @param clientIp 客户端 IP
     * @return 窗口内累计次数
     */
    private long incrementMiss(String clientIp) {
        try {
            String key = MISS_PREFIX + clientIp;
            Long count = redisService.increment(key);
            // 第一次计数时顺带设过期，之后每次自增都会沿用窗口
            if (count != null && count == 1L) {
                redisService.expire(key, Duration.ofSeconds(scanWindowSeconds));
            }
            return count == null ? 0L : count;
        } catch (Exception ex) {
            return localMiss.get(clientIp, key -> new MissCounter()).increment(scanWindowSeconds * 1000L);
        }
    }

    /**
     * 封禁该 IP。Redis 写不进去就退到本地，防护不能因此消失。
     *
     * @param clientIp 客户端 IP
     */
    private void block(String clientIp) {
        // 刚判定为扫描就必须立刻失效放行表，否则本轮封禁要等窗口过去才生效
        localClear.invalidate(clientIp);
        try {
            redisService.set(BLOCK_PREFIX + clientIp, String.valueOf(System.currentTimeMillis()), Duration.ofSeconds(blockSeconds));
        } catch (Exception ex) {
            localBlock.put(clientIp, System.currentTimeMillis() + blockSeconds * 1000L);
        }
    }

    /**
     * 构造被封禁的异常。提示语要说明原因和后果，
     * 只说「too many requests」会让正常用户以为是自己手速太快。
     *
     * @param clientIp 客户端 IP
     * @return 业务异常
     */
    private BusinessException blocked(String clientIp) {
        return new BusinessException(Constants.CODE_TOO_MANY_REQUESTS,
                "suspected key scan from " + clientIp + ", blocked for " + blockSeconds + " seconds");
    }

    /**
     * 本地未命中计数器。方法加锁是因为 Caffeine 里的值是可变对象，
     * 读改写在多线程下必须串行，否则计数会丢失。
     */
    private static final class MissCounter {

        /**
         * 窗口内累计次数。
         */
        private long count;

        /**
         * 当前窗口起始时间戳。
         */
        private long windowStartMillis = System.currentTimeMillis();

        /**
         * 累加一次计数，窗口过期则重新开始。
         *
         * @param windowMillis 窗口长度（毫秒）
         * @return 窗口内累计次数
         */
        private synchronized long increment(long windowMillis) {
            long now = System.currentTimeMillis();
            if (now - windowStartMillis > windowMillis) {
                count = 0L;
                windowStartMillis = now;
            }
            count = count + 1;
            return count;
        }

    }

}
