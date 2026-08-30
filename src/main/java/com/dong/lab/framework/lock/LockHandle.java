package com.dong.lab.framework.lock;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;

import java.util.UUID;

/**
 * 锁句柄。实现 AutoCloseable 以支持 try-with-resources，
 * 这样锁一定会被释放，不会因异常分支泄漏。
 *
 * <p>释放前必须判断 isHeldByCurrentThread：
 * 业务耗时超过 leaseTime 时锁会自动释放，此时再 unlock 会抛异常。
 */
@Slf4j
public class LockHandle implements AutoCloseable {

    private final RLock lock;

    private final String key;

    private final String token;

    private final boolean acquired;

    public LockHandle(RLock lock, String key, String token, boolean acquired) {
        this.lock = lock;
        this.key = key;
        this.token = token;
        this.acquired = acquired;
    }

    public static LockHandle failed(String key) {
        return new LockHandle(null, key, null, false);
    }

    public boolean isAcquired() {
        return acquired;
    }

    public String getToken() {
        return token;
    }

    /**
     * 释放锁。未持有或锁为 null 时直接返回，保证重复调用安全。
     */
    public void unlock() {
        if (!acquired || lock == null) {
            return;
        }
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (IllegalMonitorStateException ex) {
            log.warn("lock {} was already released, key={}", token, key);
        }
    }

    @Override
    public void close() {
        unlock();
    }

    public static String newToken() {
        return UUID.randomUUID().toString();
    }

}
