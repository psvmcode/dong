package com.dong.framework.lock;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 分布式锁服务。业务代码只依赖这一层，底层用 Redisson 实现。
 */
public interface DistributedLockService {

    /**
     * 尝试加锁。拿不到返回 failed 句柄而非抛异常，由调用方决定后续行为。
     */
    LockHandle tryLock(String key, Duration leaseTime, Duration waitTime);

    /**
     * 带返回值的加锁执行，拿不到锁直接抛业务异常。
     *
     * @param key       锁键
     * @param leaseTime 持有时间
     * @param waitTime  等待时间
     * @param supplier  业务逻辑
     * @param <T>       返回值类型
     * @return 业务返回值
     */
    default <T> T execute(String key, Duration leaseTime, Duration waitTime, Supplier<T> supplier) {
        try (LockHandle handle = tryLock(key, leaseTime, waitTime)) {
            if (!handle.isAcquired()) {
                throw new com.dong.common.exception.BusinessException(
                        com.dong.common.constant.Constants.CODE_OPERATION_CONFLICT,
                        "failed to acquire lock " + key);
            }
            return supplier.get();
        }
    }

    /**
     * 无返回值的加锁执行，拿不到锁直接抛业务异常。
     *
     * @param key       锁键
     * @param leaseTime 持有时间
     * @param waitTime  等待时间
     * @param task      业务逻辑
     */
    default void execute(String key, Duration leaseTime, Duration waitTime, Runnable task) {
        execute(key, leaseTime, waitTime, () -> {
            task.run();
            return null;
        });
    }

}
