package com.dong.lab.framework.lock;

import java.time.Duration;
import java.util.function.Supplier;

public interface DistributedLockService {

    LockHandle tryLock(String key, Duration leaseTime, Duration waitTime);

    default <T> T execute(String key, Duration leaseTime, Duration waitTime, Supplier<T> supplier) {
        try (LockHandle handle = tryLock(key, leaseTime, waitTime)) {
            if (!handle.isAcquired()) {
                throw new com.dong.lab.common.exception.BusinessException(
                        com.dong.lab.common.constant.Constants.CODE_OPERATION_CONFLICT,
                        "failed to acquire lock " + key);
            }
            return supplier.get();
        }
    }

    default void execute(String key, Duration leaseTime, Duration waitTime, Runnable task) {
        execute(key, leaseTime, waitTime, () -> {
            task.run();
            return null;
        });
    }

}
