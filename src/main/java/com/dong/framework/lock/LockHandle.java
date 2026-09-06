package com.dong.framework.lock;

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
     * Redisson 锁对象。
     */
    private final RLock lock;

    /**
     * 锁键。
     */
    private final String key;

    /**
     * 锁令牌。
     */
    private final String token;

    /**
     * 是否成功获取锁。
     */
    private final boolean acquired;

    /**
     * 构造锁句柄。
     *
     * @param lock     Redisson 锁对象
     * @param key      锁键
     * @param token    锁令牌
     * @param acquired 是否成功获取锁
     */
    public LockHandle(RLock lock, String key, String token, boolean acquired) {
        this.lock = lock;
        this.key = key;
        this.token = token;
        this.acquired = acquired;
    }

    /**
     * 创建未获取到锁的句柄。
     *
     * @param key 锁键
     * @return 失败锁句柄
     */
    public static LockHandle failed(String key) {
        return new LockHandle(null, key, null, false);
    }

    /**
     * 判断是否成功获取锁。
     *
     * @return 是否成功获取锁
     */
    public boolean isAcquired() {
        return acquired;
    }

    /**
     * 获取锁令牌。
     *
     * @return 锁令牌
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
     * try-with-resources 关闭时释放锁。
     */
    @Override
    public void close() {
        unlock();
    }

    /**
     * 生成新的锁令牌。
     *
     * @return UUID 字符串
     */
    public static String newToken() {
        return UUID.randomUUID().toString();
    }

}
