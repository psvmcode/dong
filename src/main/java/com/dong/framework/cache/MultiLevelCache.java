package com.dong.framework.cache;

import com.dong.config.ExecutorConfig;
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
 * <p>三个设计要点：
 * <ul>
 *     <li>回源时加分布式锁，保证一个 key 只有一个线程查数据库，防止缓存击穿</li>
 *     <li>写 ttl 时叠加随机抖动，避免大量 key 同时过期引发雪崩</li>
 *     <li>本地缓存无法跨节点失效，只能靠缩短生命周期 + 失效广播兜底</li>
 * </ul>
 */
@Slf4j

public class MultiLevelCache implements SmartInitializingSingleton {

    private static final String REBUILD_LOCK_PREFIX = "lab:cache:rebuild:";

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
     * 缓存配置项。
     */
    private final CacheProperties properties;

    /**
     * 缓存命中统计组件。
     */
    private final CacheStats stats;

    /**
     * 构造多级缓存。
     *
     * @param l1                    L1 本地缓存
     * @param l2                    L2 Redis 缓存
     * @param eventBus              缓存失效事件总线
     * @param distributedLockService 分布式锁服务
     * @param delayedTaskRunner     延迟任务执行器
     * @param properties            缓存配置
     * @param stats                 缓存统计组件
     */
    public MultiLevelCache(CacheStore l1,
                           CacheStore l2,
                           CacheEventBus eventBus,
                           DistributedLockService distributedLockService,
                           ExecutorConfig.DelayedTaskRunner delayedTaskRunner,
                           CacheProperties properties,
                           CacheStats stats) {
        this.l1 = l1;
        this.l2 = l2;
        this.eventBus = eventBus;
        this.distributedLockService = distributedLockService;
        this.delayedTaskRunner = delayedTaskRunner;
        this.properties = properties;
        this.stats = stats;
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
     * 依次查询 L1、L2、回源，逐层回填。返回 null 表示数据不存在，
     * 调用方需要自己决定是抛异常还是返回空。
     */
    public <T> T get(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        if (l1 != null) {
            CacheLookup<T> lookup = l1.lookup(key, type);
            if (lookup instanceof CacheLookup.Hit<T> hit) {
                stats.recordL1Hit();
                return hit.value();
            }
            if (lookup instanceof CacheLookup.Empty<T>) {
                stats.recordL1Hit();
                stats.recordPenetrationBlocked();
                return null;
            }
        }

        if (l2 != null) {
            CacheLookup<T> lookup = l2.lookup(key, type);
            if (lookup instanceof CacheLookup.Hit<T> hit) {
                stats.recordL2Hit();
                backfillL1(key, hit.value(), ttl);
                return hit.value();
            }
            if (lookup instanceof CacheLookup.Empty<T>) {
                stats.recordL2Hit();
                stats.recordPenetrationBlocked();
                putEmptyL1(key);
                return null;
            }
        }

        stats.recordMiss();
        T loaded = rebuildUnderLock(key, type, loader);
        if (loaded == null) {
            putEmpty(key);
            return null;
        }
        writeThrough(key, loaded, ttl);
        return loaded;
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
     * 回源。必须加锁，否则缓存击穿时全部请求会同时打到数据库。
     * 拿到锁后再查一次 L2，是因为等待期间可能已有线程把数据写好了，没必要重复回源。
     */
    private <T> T rebuildUnderLock(String key, Class<T> type, Supplier<T> loader) {
        try (LockHandle handle = distributedLockService.tryLock(REBUILD_LOCK_PREFIX + key,
                properties.getRebuildLease(), properties.getRebuildWait())) {
            if (l2 != null && l2.lookup(key, type) instanceof CacheLookup.Hit<T> hit) {
                stats.recordL2Hit();
                return hit.value();
            }
            stats.recordRebuild();
            return loader.get();
        }
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
