package com.dong.lab.framework.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁实现，基于 Redisson 的 RLock。
 *
 * <p>两个必须注意的点：
 * leaseTime 是锁的自动释放时间，业务没跑完锁也会被释放，需要续期就得用看门狗模式；
 * 调用方必须区分"没拿到锁"和"异常"，这里没拿到锁返回 failed 句柄而不是抛异常，
 * 由调用方决定是重试还是快速失败。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockServiceImpl implements DistributedLockService {

    private final RedissonClient redissonClient;

    /**
     * 等待 waitTime 拿锁，拿到后持有 leaseTime。
     * 中断时恢复中断标记再抛异常，不能吞掉中断状态。
     */
    @Override
    public LockHandle tryLock(String key, Duration leaseTime, Duration waitTime) {
        RLock lock = redissonClient.getLock(key);
        boolean acquired;
        try {
            acquired = lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new com.dong.lab.common.exception.BusinessException(
                    com.dong.lab.common.constant.Constants.CODE_OPERATION_CONFLICT,
                    "interrupted while waiting for lock " + key);
        }

        if (!acquired) {
            return LockHandle.failed(key);
        }
        return new LockHandle(lock, key, LockHandle.newToken(), true);
    }

}
