package com.dong.framework.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 定时任务分布式锁切面。
 *
 * <p>只拦截拿锁阶段的异常并跳过本轮；业务方法自身抛出的异常照常向外传播，
 * 由任务内部的 try/catch 按自己的语义处理，切面不吞业务异常。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DistributedTaskLockAspect {

    /**
     * 锁键统一前缀，避免与业务锁重名。
     */
    private static final String KEY_PREFIX = "lab:task:";

    /**
     * distributedLockService，分布式锁服务。
     */
    private final DistributedLockService distributedLockService;

    /**
     * 拿到锁才执行任务，拿不到就跳过本轮。
     *
     * @param joinPoint 切点
     * @param taskLock  锁注解
     * @return 任务返回值
     * @throws Throwable 业务异常照常抛出
     */
    @Around("@annotation(taskLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedTaskLock taskLock) throws Throwable {
        String key = KEY_PREFIX + taskLock.value();
        LockHandle handle;
        try {
            handle = distributedLockService.tryLock(key,
                    Duration.ofMillis(Math.max(1000L, taskLock.leaseMs())), Duration.ZERO);
        } catch (RuntimeException ex) {
            log.warn("task lock unavailable, skip this round key={}", key, ex);
            return null;
        }
        try (LockHandle locked = handle) {
            if (!locked.isAcquired()) {
                log.debug("task skipped, another instance is running key={}", key);
                return null;
            }
            return joinPoint.proceed();
        }
    }

}
