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
         * 在途延迟任务上限。ScheduledThreadPoolExecutor 的延迟队列无法设置有界容量，
         * 只能在提交侧计数：写缓存双删在高并发下会一直往队列里塞任务。
         */
        private static final int MAX_PENDING = 10_000;

        /**
         * 调度器。
         */
        private final ScheduledExecutorService scheduler;

        /**
         * 在途任务计数。
         */
        private final java.util.concurrent.atomic.AtomicLong pending = new java.util.concurrent.atomic.AtomicLong();

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
            if (pending.incrementAndGet() > MAX_PENDING) {
                pending.decrementAndGet();
                org.slf4j.LoggerFactory.getLogger(DelayedTaskRunner.class).warn("too many pending delayed tasks, task dropped");
                return;
            }
            try {
                scheduler.schedule(() -> {
                    try {
                        task.run();
                    } finally {
                        pending.decrementAndGet();
                    }
                }, delay.toMillis(), TimeUnit.MILLISECONDS);
            } catch (RuntimeException ex) {
                pending.decrementAndGet();
                org.slf4j.LoggerFactory.getLogger(DelayedTaskRunner.class).error("failed to schedule delayed task", ex);
            }
        }

    }

}
