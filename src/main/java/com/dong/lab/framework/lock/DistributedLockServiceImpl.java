package com.dong.lab.framework.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockServiceImpl implements DistributedLockService {

    private final RedissonClient redissonClient;

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
