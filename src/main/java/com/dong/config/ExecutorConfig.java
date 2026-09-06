package com.dong.config;

import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
/**
 * 线程池与延迟任务配置类。
 */
@Configuration
@EnableAsync

public class ExecutorConfig {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, factory("delayed-task"));

    /**
     * 创建延迟任务执行器。
     *
     * @return 延迟任务执行器
     */
    @Bean
    public DelayedTaskRunner delayedTaskRunner() {
        return new DelayedTaskRunner(scheduler);
    }

    /**
     * 关闭调度器。
     */
    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }

    /**
     * 创建命名线程工厂。
     *
     * @param prefix 线程名前缀
     * @return 线程工厂
     */
    private static ThreadFactory factory(String prefix) {
        AtomicLong counter = new AtomicLong();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    public static class DelayedTaskRunner {

        /**
         * 调度器。
         */
        private final ScheduledExecutorService scheduler;

        /**
         * 构造延迟任务执行器。
         *
         * @param scheduler 调度器
         */
        public DelayedTaskRunner(ScheduledExecutorService scheduler) {
            this.scheduler = scheduler;
        }

        /**
         * 在指定延迟后执行任务。
         *
         * @param delay 延迟时间
         * @param task  待执行任务
         */
        public void runAfter(Duration delay, Runnable task) {
            scheduler.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
        }

    }

}
