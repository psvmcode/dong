package com.dong.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent 工具执行线程池。
 *
 * <p>必须显式定义有界线程池：容器里没有 TaskExecutor bean 时 Spring Boot 用的是
 * SimpleAsyncTaskExecutor，每次提交都新建线程且没有上限。
 * 一轮几十个工具调用挂在它上面，线程数会被打飞。
 *
 * <p>拒绝策略用 CallerRuns 而不是抛异常：队列满时退回调用线程串行执行，
 * 慢一点但任务不会丢，也不会因为有线程池就把并行实验变成「炸掉」。
 */
@Configuration
public class AgentExecutorConfig {

    /**
     * 线程数。
     */
    @Value("${dong.agent.tool-pool-size:8}")
    private int poolSize;

    /**
     * 队列容量。
     */
    @Value("${dong.agent.tool-queue-capacity:64}")
    private int queueCapacity;

    /**
     * 运行调度线程数，与并发运行上限一致。
     */
    @Value("${dong.agent.max-concurrent-runs:8}")
    private int runPoolSize;

    /**
     * 创建工具执行线程池。
     *
     * @return 线程池
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService agentToolExecutor() {
        return new ThreadPoolExecutor(poolSize, poolSize, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity), factory("agent-tool"),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * 创建运行调度线程池。与工具池分开：一次运行会在整个循环期间占住一个调度线程，
     * 和工具调用共用同一个池的话，并发跑几个会话就能把池占满，
     * 表现为工具调用排不上队而循环又退不出来。
     *
     * @return 线程池
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService agentRunExecutor() {
        return new ThreadPoolExecutor(runPoolSize, runPoolSize, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(runPoolSize * 2), factory("agent-run"),
                new ThreadPoolExecutor.AbortPolicy());
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

}
