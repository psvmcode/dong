package com.dong.lab.config;

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
 * ExecutorConfig，配置类。
 */
@Configuration
@EnableAsync

public class ExecutorConfig {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, factory("delayed-task"));

    @Bean
    /**
     * delayedTaskRunner。
     */
    public DelayedTaskRunner delayedTaskRunner() {
        return new DelayedTaskRunner(scheduler);
    }

    @PreDestroy
    /**
     * shutdown。
     */
    public void shutdown() {
        scheduler.shutdown();
    }

    /**
     * factory。
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
     * scheduler。
     */
        private final ScheduledExecutorService scheduler;
        public DelayedTaskRunner(ScheduledExecutorService scheduler) {
            this.scheduler = scheduler;
        }

        public void runAfter(Duration delay, Runnable task) {
            scheduler.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
        }

    }

}
