package com.dong.lab.classic.service.impl;

import com.dong.lab.classic.service.LockLabService;
import com.dong.lab.framework.lock.DistributedLockService;
import com.dong.lab.framework.lock.LockHandle;
import com.dong.lab.framework.redis.RedisService;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class LockLabServiceImpl implements LockLabService {

    private static final String COUNTER = "lab:lock:counter:";

    private static final Duration LEASE_TIME = Duration.ofSeconds(10);

    private static final Duration WAIT_TIME = Duration.ofSeconds(30);

    private final RedisService redisService;

    private final DistributedLockService distributedLockService;

    @Override
    public Map<String, Object> withoutLock(int threads, int loops) {
        return run(threads, loops, false);
    }

    @Override
    public Map<String, Object> withLock(int threads, int loops) {
        return run(threads, loops, true);
    }

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

    private void increment(String key) {
        long current = Long.parseLong(redisService.get(key).orElse("0"));
        redisService.set(key, String.valueOf(current + 1));
    }

    private static ThreadFactory factory() {
        AtomicLong counter = new AtomicLong();
        return runnable -> {
            Thread thread = new Thread(runnable, "lock-lab-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

}
