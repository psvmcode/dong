package com.dong.classic.service.impl;

import com.dong.classic.service.LockLabService;
import com.dong.framework.lock.DistributedLockService;
import com.dong.framework.lock.LockHandle;
import com.dong.framework.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
/**
 * 分布式锁对照实验。不加锁的模式会大量丢失更新，加锁的模式结果精确，
 * 代价是耗时高出一到两个数量级，这就是正确性的成本。
 *
 * <p>两个关键实现约束：
 * 必须用平台线程而非虚拟线程，因为 Redisson 可重入锁依赖线程 id 标识持有者，
 * 而虚拟线程的 id 不保证唯一；
 * 结果必须区分拿到锁与等待超时，否则超时线程会被静默计入丢失更新，实验结论失真。
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class LockLabServiceImpl implements LockLabService {

    private static final String COUNTER = "lab:lock:counter:";

    private static final Duration LEASE_TIME = Duration.ofSeconds(10);

    private static final Duration WAIT_TIME = Duration.ofSeconds(30);

    /**
     * Redis 服务。
     */
    private final RedisService redisService;

    /**
     * 分布式锁服务。
     */
    private final DistributedLockService distributedLockService;

    /**
     * 执行不加锁的并发自增对照实验。
     *
     * @param threads 线程数
     * @param loops   每线程循环数
     * @return 实验结果
     */
    @Override
    public Map<String, Object> withoutLock(int threads, int loops) {
        return run(threads, loops, false);
    }

    /**
     * 执行加锁的并发自增实验。
     *
     * @param threads 线程数
     * @param loops   每线程循环数
     * @return 实验结果
     */
    @Override
    public Map<String, Object> withLock(int threads, int loops) {
        return run(threads, loops, true);
    }

    /**
     * 运行并发自增实验并汇总结果。
     *
     * @param threads 线程数
     * @param loops   每线程循环数
     * @param guarded 是否加锁
     * @return 实验结果
     */
    private Map<String, Object> run(int threads, int loops, boolean guarded) {
        String key = COUNTER + System.nanoTime();
        redisService.set(key, "0");
        int total = Math.max(1, threads) * Math.max(1, loops);
        long start = System.currentTimeMillis();
        AtomicLong succeeded = new AtomicLong();
        AtomicLong timedOut = new AtomicLong();
        try (ExecutorService executor = Executors.newFixedThreadPool(Math.min(total, 64), factory())) {
            CountDownLatch latch = new CountDownLatch(total);
            for (int i = 0; i < total; i++) {
                executor.submit(() -> {
                    try {
                        if (incrementUnder(key, guarded)) {
                            succeeded.incrementAndGet();
                        } else {
                            timedOut.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        long actual = Long.parseLong(redisService.get(key).orElse("0"));
        redisService.delete(key);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", guarded ? "redisson-lock" : "no-lock");
        result.put("expected", total);
        result.put("actual", actual);
        result.put("lockAcquired", succeeded.get());
        result.put("lockTimedOut", guarded ? timedOut.get() : 0);
        result.put("lostUpdates", total - actual);
        result.put("elapsedMillis", System.currentTimeMillis() - start);
        log.info("lock lab mode={} expected={} actual={} acquired={} timedOut={}",
                result.get("mode"), total, actual, succeeded.get(), timedOut.get());
        return result;
    }

    /**
     * 读改写演示。不加锁时这里的 get 与 set 之间存在竞态窗口，
     * 两个线程可能读到同一个值再各自加一，导致更新丢失。
     */
    private boolean incrementUnder(String key, boolean guarded) {
        if (!guarded) {
            increment(key);
            return true;
        }

        try (LockHandle handle = distributedLockService.tryLock(key + ":lock", LEASE_TIME, WAIT_TIME)) {
            if (!handle.isAcquired()) {
                return false;
            }
            increment(key);
            return true;
        }
    }

    /**
     * 不加锁的读改写。get 与 set 之间存在竞态窗口，
     * 两个线程可能读到同一个值再各自加一，丢失一次更新。
     * 这正是这个实验要演示的问题。
     */
    private void increment(String key) {
        long current = Long.parseLong(redisService.get(key).orElse("0"));
        redisService.set(key, String.valueOf(current + 1));
    }

    /**
     * 刻意使用平台线程池。虚拟线程的 id 不唯一，
     * 会导致 Redisson 可重入锁无法识别持有者，锁直接失效。
     */
    private static ThreadFactory factory() {
        AtomicLong counter = new AtomicLong();
        return runnable -> {
            Thread thread = new Thread(runnable, "lock-lab-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

}
