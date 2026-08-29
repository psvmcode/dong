package com.dong.lab.framework.lock;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;

import java.util.UUID;

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
