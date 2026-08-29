package com.dong.lab.framework.cache;

import com.dong.lab.config.ExecutorConfig;
import com.dong.lab.framework.lock.DistributedLockService;
import com.dong.lab.framework.lock.LockHandle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Slf4j
public class MultiLevelCache implements SmartInitializingSingleton {

    private static final String REBUILD_LOCK_PREFIX = "lab:cache:rebuild:";

    private static final long L1_TTL_CAP_MILLIS = 60_000L;

    private final CacheStore l1;

    private final CacheStore l2;

    private final CacheEventBus eventBus;

    private final DistributedLockService distributedLockService;

    private final ExecutorConfig.DelayedTaskRunner delayedTaskRunner;

    private final CacheProperties properties;

    private final CacheStats stats;

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

    @Override
    public void afterSingletonsInstantiated() {
        eventBus.register(event -> {
            if (l1 != null) {
                l1.evict(event.key());
            }
        });
    }

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

    public <T> T get(String key, Class<T> type, Supplier<T> loader) {
        return get(key, type, properties.getDefaultTtl(), loader);
    }

    public void invalidate(String key) {
        if (l1 != null) {
            l1.evict(key);
        }
        if (l2 != null) {
            l2.evict(key);
        }
        eventBus.publishInvalidation(key);
    }

    public void invalidateEventually(String key) {
        invalidate(key);
        delayedTaskRunner.runAfter(properties.getDoubleDeleteDelay(), () -> invalidate(key));
    }

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

    private void writeThrough(String key, Object value, Duration ttl) {
        if (l2 != null) {
            l2.put(key, value, ttl);
        }
        backfillL1(key, value, ttl);
    }

    private void putEmpty(String key) {
        if (l2 != null) {
            l2.putEmpty(key, properties.getNullValueTtl());
        }
        putEmptyL1(key);
    }

    private void putEmptyL1(String key) {
        if (l1 != null) {
            l1.putEmpty(key, properties.getNullValueTtl());
        }
    }

    private void backfillL1(String key, Object value, Duration ttl) {
        if (l1 != null) {
            l1.put(key, value, l1Ttl(ttl));
        }
    }

    private Duration l1Ttl(Duration ttl) {
        long capped = Math.min(Math.max(1000L, ttl.toMillis()), L1_TTL_CAP_MILLIS);
        if (properties.getTtlJitterRatio() <= 0) {
            return Duration.ofMillis(capped);
        }
        long jitter = (long) (capped * properties.getTtlJitterRatio() * ThreadLocalRandom.current().nextDouble());
        return Duration.ofMillis(capped + jitter);
    }

}
