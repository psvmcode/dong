package com.dong.framework.cache;

import com.dong.config.ExecutorConfig;
import com.dong.framework.limiter.RateLimitAlgorithm;
import com.dong.framework.limiter.RateLimitManager;
import com.dong.framework.limiter.RateLimitRule;
import com.dong.framework.lock.DistributedLockService;
import com.dong.framework.lock.LockHandle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 多级缓存，读取顺序为 L1 本地缓存、L2 分布式缓存、回源数据库。
 *
 * <p>四个设计要点：
 * <ul>
 *     <li>回源时加分布式锁，保证一个 key 只有一个线程查数据库，防止缓存击穿</li>
 *     <li>写 ttl 时叠加随机抖动，避免大量 key 同时过期引发雪崩</li>
 *     <li>本地缓存无法跨节点失效，只能靠缩短生命周期 + 失效广播兜底</li>
 *     <li>回源前面还有熔断和频控两道闸门，下游故障时不把压力全放下去</li>
 * </ul>
 *
 * <p>关于降级，这里做了一个明确取舍：
 * 缓存全部失效且数据库也查不到时，不假装成功，而是返回
 * {@link CacheResolution.Unavailable} 让调用方明确失败；
 * 只有确实拿到过旧值时才返回 {@link CacheResolution.Stale}，
 * 并且由调用方把「数据可能是旧的」这件事告诉用户。
 * 「保证每次都返回数据」看着友好，实际上是用错误数据掩盖故障。
 */
@Slf4j
public class MultiLevelCache implements SmartInitializingSingleton {

    private static final String REBUILD_LOCK_PREFIX = "lab:cache:rebuild:";
    private static final String REBUILD_GLOBAL_KEY = "lab:cache:rebuild:global";
    private static final String REBUILD_KEY_PREFIX = "lab:cache:rebuild:key:";

    // 本地缓存跨节点无法感知失效，生命周期压到 60 秒，是正确性对性能的妥协
    private static final long L1_TTL_CAP_MILLIS = 60_000L;

    /**
     * L1 本地缓存。
     */
    private final CacheStore l1;

    /**
     * L2 Redis 缓存。
     */
    private final CacheStore l2;

    /**
     * 缓存失效事件总线。
     */
    private final CacheEventBus eventBus;

    /**
     * 分布式锁服务。
     */
    private final DistributedLockService distributedLockService;

    /**
     * 延迟任务执行器。
     */
    private final ExecutorConfig.DelayedTaskRunner delayedTaskRunner;

    /**
     * 限流管理器，用于回源频控。
     */
    private final RateLimitManager rateLimitManager;

    /**
     * 缓存配置项。
     */
    private final CacheProperties properties;

    /**
     * 缓存命中统计组件。
     */
    private final CacheStats stats;

    /**
     * 回源熔断器，保护数据库。
     */
    private final CacheCircuitBreaker sourceBreaker;

    /**
     * 构造多级缓存。
     *
     * @param l1                     L1 本地缓存
     * @param l2                     L2 Redis 缓存
     * @param eventBus               缓存失效事件总线
     * @param distributedLockService 分布式锁服务
     * @param delayedTaskRunner      延迟任务执行器
     * @param rateLimitManager       限流管理器
     * @param properties             缓存配置
     * @param stats                  缓存统计组件
     * @param sourceBreaker          回源熔断器
     */
    public MultiLevelCache(CacheStore l1, CacheStore l2, CacheEventBus eventBus, DistributedLockService distributedLockService, ExecutorConfig.DelayedTaskRunner delayedTaskRunner, RateLimitManager rateLimitManager, CacheProperties properties, CacheStats stats, CacheCircuitBreaker sourceBreaker) {
        this.l1 = l1;
        this.l2 = l2;
        this.eventBus = eventBus;
        this.distributedLockService = distributedLockService;
        this.delayedTaskRunner = delayedTaskRunner;
        this.rateLimitManager = rateLimitManager;
        this.properties = properties;
        this.stats = stats;
        this.sourceBreaker = sourceBreaker;
    }

    /**
     * 订阅其他节点发来的失效事件。L2 由 Redis 统一持有无需处理，
     * 这里只需清理各节点自己的 L1，否则同一份数据在多个节点间会不一致。
     */
    @Override
    public void afterSingletonsInstantiated() {
        eventBus.register(event -> {
            if (l1 != null) {
                l1.evict(event.key());
            }
        });
    }

    /**
     * 依次查询 L1、L2、回源，逐层回填，并把降级情况如实告诉调用方。
     *
     * @param key    缓存键
     * @param type   值类型
     * @param ttl    过期时间
     * @param loader 回源加载器
     * @param <T>    值类型
     * @return 读取结果，区分新鲜值、旧值、确认不存在与依赖不可用
     */
    public <T> CacheResolution<T> resolve(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        T stale = null;
        if (l1 != null) {
            CacheLookup<T> lookup = l1.lookup(key, type);
            if (lookup instanceof CacheLookup.Hit<T> hit) {
                stats.recordL1Hit();
                return new CacheResolution.Fresh<>(hit.value());
            }
            if (lookup instanceof CacheLookup.Stale<T> old) {
                stale = old.value();
            }
            if (lookup instanceof CacheLookup.Empty<T>) {
                stats.recordL1Hit();
                stats.recordPenetrationBlocked();
                return new CacheResolution.Absent<>();
            }
        }

        if (l2 != null) {
            CacheLookup<T> lookup = l2.lookup(key, type);
            if (lookup instanceof CacheLookup.Hit<T> hit) {
                stats.recordL2Hit();
                backfillL1(key, hit.value(), ttl);
                return new CacheResolution.Fresh<>(hit.value());
            }
            if (lookup instanceof CacheLookup.Stale<T> old && stale == null) {
                stale = old.value();
            }
            if (lookup instanceof CacheLookup.Empty<T>) {
                stats.recordL2Hit();
                stats.recordPenetrationBlocked();
                putEmptyL1(key);
                return new CacheResolution.Absent<>();
            }
        }

        stats.recordMiss();
        return rebuild(key, type, ttl, loader, stale);
    }

    /**
     * 读取缓存，降级与不存在都折叠为 null。
     * 只适合不关心降级细节的场景，面向用户的读路径请用 resolve。
     *
     * @param key    缓存键
     * @param type   值类型
     * @param ttl    过期时间
     * @param loader 回源加载器
     * @param <T>    值类型
     * @return 缓存值或 null
     */
    public <T> T get(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        return switch (resolve(key, type, ttl, loader)) {
            case CacheResolution.Fresh<T> fresh -> fresh.value();
            case CacheResolution.Stale<T> stale -> stale.value();
            case CacheResolution.Absent<T> absent -> null;
            case CacheResolution.Unavailable<T> unavailable -> null;
        };
    }

    /**
     * 读取缓存，使用默认 TTL。
     *
     * @param key    缓存键
     * @param type   值类型
     * @param loader 回源加载器
     * @param <T>    值类型
     * @return 缓存值或 null
     */
    public <T> T get(String key, Class<T> type, Supplier<T> loader) {
        return get(key, type, properties.getDefaultTtl(), loader);
    }

    /**
     * 回源熔断器当前状态，供运维观察。
     *
     * @return CLOSED、OPEN 或 HALF_OPEN
     */
    public String sourceBreakerState() {
        return sourceBreaker.state();
    }

    /**
     * 立即失效。先清本地两层，再广播给其他节点清各自的 L1。
     */
    public void invalidate(String key) {
        if (l1 != null) {
            l1.evict(key);
        }
        if (l2 != null) {
            l2.evict(key);
        }
        eventBus.publishInvalidation(key);
    }

    /**
     * 延迟双删。第二次删除是为了清掉这类残留：
     * 某个读请求在更新提交前读到了旧值，之后又把它写回了缓存。
     * 这是缓存与数据库双写一致的兜底手段，不能保证强一致。
     */
    public void invalidateEventually(String key) {
        invalidate(key);
        delayedTaskRunner.runAfter(properties.getDoubleDeleteDelay(), () -> invalidate(key));
    }

    /**
     * 回源。三道闸门依次是熔断、频控、重建锁：
     * 熔断挡住已经故障的下游，频控挡住超出数据库能力的多余流量，
     * 重建锁保证同一个 key 只有一个线程真的去查。
     */
    private <T> CacheResolution<T> rebuild(String key, Class<T> type, Duration ttl, Supplier<T> loader, T stale) {
        if (!allowRebuild(key)) {
            return fallback(stale);
        }
        try (LockHandle handle = acquireRebuildLock(key)) {
            // handle 为 null 表示锁服务不可用，这时退化为无锁回源：
            // 锁只影响击穿保护，不影响数据正确性，
            // 而 Redis 故障不该连带把「数据库还正常」的读链路一起掐断
            if (handle != null && !handle.isAcquired()) {
                // 已经有线程在重建了，本线程不该跟着一起打数据库
                stats.recordRebuildSkipped();
                return fallback(stale);
            }
            if (l2 != null && l2.lookup(key, type) instanceof CacheLookup.Hit<T> hit) {
                stats.recordL2Hit();
                return new CacheResolution.Fresh<>(hit.value());
            }
            stats.recordRebuild();
            T loaded = loader.get();
            sourceBreaker.recordSuccess();
            if (loaded == null) {
                putEmpty(key);
                return new CacheResolution.Absent<>();
            }
            writeThrough(key, loaded, ttl);
            return new CacheResolution.Fresh<>(loaded);
        } catch (Exception ex) {
            // 回源失败绝不能写空值标记：那等于把一次故障固化成"数据不存在"，
            // 之后的请求在标记过期前都会拿到错误答案
            sourceBreaker.recordFailure();
            log.warn("cache rebuild failed key={} staleAvailable={}: {}", key, stale != null, ex.getMessage());
            return fallback(stale);
        }
    }

    /**
     * 获取重建锁。锁服务异常时返回 null，由调用方退化为无锁回源。
     *
     * @param key 缓存键
     * @return 锁句柄，null 表示锁服务不可用
     */
    private LockHandle acquireRebuildLock(String key) {
        try {
            return distributedLockService.tryLock(REBUILD_LOCK_PREFIX + key, properties.getRebuildLease(), properties.getRebuildWait());
        } catch (Exception ex) {
            log.warn("rebuild lock unavailable key={}, rebuilding without lock: {}", key, ex.getMessage());
            return null;
        }
    }

    /**
     * 判断是否允许回源。熔断优先于频控：下游已经故障时没必要再数配额。
     *
     * @param key 缓存键
     * @return 允许返回 true
     */
    private boolean allowRebuild(String key) {
        if (properties.isBreakerEnabled() && !sourceBreaker.allowRequest()) {
            stats.recordCircuitBlocked();
            return false;
        }
        return tryAcquireQuota(REBUILD_GLOBAL_KEY, properties.getRebuildGlobalLimitPerSecond())
                && tryAcquireQuota(REBUILD_KEY_PREFIX + key, properties.getRebuildKeyLimitPerSecond());
    }

    /**
     * 获取回源配额。刻意走本地限流：这道闸门是为了保护数据库，
     * 它自己不能再依赖 Redis，否则 Redis 故障时限流会跟着一起失效。
     *
     * @param key              限流键
     * @param permitsPerSecond 每秒配额
     * @return 是否拿到配额
     */
    private boolean tryAcquireQuota(String key, long permitsPerSecond) {
        if (permitsPerSecond <= 0) {
            return true;
        }
        boolean allowed = rateLimitManager.tryAcquire(key, RateLimitRule.perSecond(permitsPerSecond, RateLimitAlgorithm.TOKEN_BUCKET), false);
        if (!allowed) {
            stats.recordRebuildRejected();
        }
        return allowed;
    }

    /**
     * 兜底。有旧值就把旧值交出去并标记降级，没有才承认失败。
     *
     * @param stale 可用的旧值，可能为 null
     * @param <T>   值类型
     * @return 降级或不可用结果
     */
    private <T> CacheResolution<T> fallback(T stale) {
        if (stale == null) {
            stats.recordDegraded();
            return new CacheResolution.Unavailable<>();
        }
        stats.recordStaleServed();
        return new CacheResolution.Stale<>(stale);
    }

    /**
     * 写入缓存，先写 L2 再回填 L1。
     *
     * @param key   缓存键
     * @param value 缓存值
     * @param ttl   过期时间
     */
    private void writeThrough(String key, Object value, Duration ttl) {
        if (l2 != null) {
            l2.put(key, value, ttl);
        }
        backfillL1(key, value, ttl);
    }

    /**
     * 缓存空值。数据库查不到时也写一份标记，
     * 这样针对同一个不存在 id 的重复攻击不会再打到数据库，是防穿透的手段之一。
     *
     * @param key 缓存键
     */
    private void putEmpty(String key) {
        if (l2 != null) {
            l2.putEmpty(key, properties.getNullValueTtl());
        }
        putEmptyL1(key);
    }

    /**
     * 在 L1 写入空值标记。
     *
     * @param key 缓存键
     */
    private void putEmptyL1(String key) {
        if (l1 != null) {
            l1.putEmpty(key, properties.getNullValueTtl());
        }
    }

    /**
     * 将 L2 命中的数据回填到 L1。
     *
     * @param key   缓存键
     * @param value 缓存值
     * @param ttl   过期时间
     */
    private void backfillL1(String key, Object value, Duration ttl) {
        if (l1 != null) {
            l1.put(key, value, l1Ttl(ttl));
        }
    }

    /**
     * 计算本地缓存的实际 ttl。先夹到上下限内，再叠加随机抖动。
     * 抖动的作用是让一批同时写入的 key 分散过期，避免集中失效打穿数据库（雪崩）。
     */
    private Duration l1Ttl(Duration ttl) {
        long capped = Math.min(Math.max(1000L, ttl.toMillis()), L1_TTL_CAP_MILLIS);
        if (properties.getTtlJitterRatio() <= 0) {
            return Duration.ofMillis(capped);
        }
        long jitter = (long) (capped * properties.getTtlJitterRatio() * ThreadLocalRandom.current().nextDouble());
        return Duration.ofMillis(capped + jitter);
    }

}
