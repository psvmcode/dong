package com.dong.framework.lock;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 定时任务分布式锁注解。
 *
 * <p>多实例部署时同一时刻只让一个实例执行任务。与在任务方法里手写锁相比，
 * 注解把「拿锁、判空、释放」收敛到一处，任务本身只关心业务。
 *
 * <p>锁不可用时跳过本轮而不是无锁执行：这类任务大多是补偿、重建、清理，
 * 并发执行比晚一轮执行的代价大得多。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedTaskLock {

    /**
     * 锁名，全局唯一，切面会自动加统一前缀。
     *
     * @return 锁名
     */
    String value();

    /**
     * 持锁时间，单位毫秒。必须长于任务正常耗时，短于两轮调度间隔。
     *
     * @return 持锁时间
     */
    long leaseMs() default 120_000L;

}
