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

    /**
     * lock。
     */
    private final RLock lock;

    /**
     * key。
     */
    private final String key;

    /**
     * token。
     */
    private final String token;

    /**
     * acquired。
     */
    private final boolean acquired;

    public LockHandle(RLock lock, String key, String token, boolean acquired) {
        this.lock = lock;
        this.key = key;
        this.token = token;
        this.acquired = acquired;
    }

    /**
     * failed。
     */
    public static LockHandle failed(String key) {
        return new LockHandle(null, key, null, false);
    }

    /**
     * isAcquired。
     */
    public boolean isAcquired() {
        return acquired;
    }

    /**
     * getToken。
     */
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

    /**
     * close。
     */
    @Override
    public void close() {
        unlock();
    }

    /**
     * newToken。
     */
    public static String newToken() {
        return UUID.randomUUID().toString();
    }

}
