package com.dong.framework.mq.impl;

import com.dong.common.util.JsonUtils;
import com.dong.framework.mq.MessageHandler;
import com.dong.framework.mq.MessageProducer;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本地消息总线。在 JVM 内完成投递，不需要任何中间件，
 * 因此默认配置下也能跑通全部消息流程。
 *
 * <p>仅用于开发验证：消息存在内存里，进程重启即丢失，也不跨节点。
 *
 * <p>所有队列必须有界：默认单线程执行器的 LinkedBlockingQueue 没有上限，
 * handler 变慢时堆积的任务会一路吃到 OOM，开发用的总线也不能例外。
 */
@Slf4j
@Component
public class LocalMessageBus implements MessageProducer {

    /**
     * 每个顺序分片允许排队的任务数。
     */
    private static final int MAX_QUEUE_PER_SHARD = 1_000;

    /**
     * 在途延迟任务上限。延迟队列本身无法设容量，只能在提交侧计数。
     */
    private static final int MAX_DELAY_TASKS = 10_000;

    private final Map<String, List<MessageHandler>> handlers = new ConcurrentHashMap<>();

    private final ScheduledExecutorService delayScheduler = Executors.newScheduledThreadPool(2, factory("local-bus-delay"));

    private final AtomicLong pendingDelays = new AtomicLong();

    // 顺序消息按分片交给固定的单线程执行器，同一分片串行执行从而保证顺序
    private final Map<Integer, ExecutorService> orderedExecutors = new ConcurrentHashMap<>();

    private final AtomicLong dispatched = new AtomicLong();

    /**
     * 注册消息处理器。
     *
     * @param handler 消息处理器
     */
    public void register(MessageHandler handler) {
        handlers.computeIfAbsent(handler.topic(), topic -> new CopyOnWriteArrayList<>()).add(handler);
        log.info("handler {} registered on topic {}", handler.getClass().getSimpleName(), handler.topic());
    }

    /**
     * 发送普通消息。
     *
     * @param topic   主题
     * @param key     业务键
     * @param payload 消息体
     */
    @Override
    public void send(String topic, String key, Object payload) {
        dispatch(topic, key, payload);
    }

    /**
     * 发送延迟消息。
     *
     * @param topic   主题
     * @param key     业务键
     * @param payload 消息体
     * @param delay   延迟时长
     */
    @Override
    public void sendDelayed(String topic, String key, Object payload, Duration delay) {
        if (pendingDelays.incrementAndGet() > MAX_DELAY_TASKS) {
            pendingDelays.decrementAndGet();
            log.warn("local bus delayed tasks exceed {}, message dropped topic={}", MAX_DELAY_TASKS, topic);
            return;
        }
        try {
            delayScheduler.schedule(() -> {
                try {
                    safeDispatch(topic, key, payload);
                } finally {
                    pendingDelays.decrementAndGet();
                }
            }, delay.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RuntimeException ex) {
            pendingDelays.decrementAndGet();
            log.error("local bus delay schedule failed topic={}", topic, ex);
        }
    }

    /**
     * 发送顺序消息。按 shardingKey 哈希到固定分片，
     * 每个分片一个单线程执行器，相同 key 因此串行执行。
     *
     * @param topic       主题
     * @param key         业务键
     * @param payload     消息体
     * @param shardingKey 分片键
     */
    @Override
    public void sendOrdered(String topic, String key, Object payload, String shardingKey) {
        int shard = Math.floorMod(shardingKey == null ? 0 : shardingKey.hashCode(), 4);
        orderedExecutors.computeIfAbsent(shard, this::newOrderedExecutor).submit(() -> safeDispatch(topic, key, payload));
    }

    /**
     * 返回消息生产者名称。
     *
     * @return 消息生产者名称
     */
    @Override
    public String name() {
        return "local";
    }

    /**
     * 获取已投递消息数量。
     *
     * @return 已投递消息数量
     */
    public long dispatchedCount() {
        return dispatched.get();
    }

    /**
     * 投递消息。单个 handler 抛异常不能影响其他 handler，因此逐个捕获；
     * 但捕获后消息即丢失，真实 MQ 需要重试或进死信队列。
     *
     * @param topic   主题
     * @param key     业务键
     * @param payload 消息体
     */
    private void dispatch(String topic, String key, Object payload) {
        List<MessageHandler> listeners = handlers.get(topic);
        if (listeners == null || listeners.isEmpty()) {
            log.warn("no handler registered for topic {}, message dropped", topic);
            return;
        }
        String body = payload instanceof String text ? text : JsonUtils.toJson(payload);
        dispatched.incrementAndGet();
        listeners.forEach(handler -> {
            try {
                handler.handle(key, body);
            } catch (Exception ex) {
                log.error("handler {} failed on topic {}", handler.getClass().getSimpleName(), topic, ex);
            }
        });
    }

    /**
     * 异步投递的兜底。submit/schedule 抛出的运行时异常会被 Future 吞掉，
     * 不在这里捕获就只表现为「消息静默消失、日志里什么都没有」。
     *
     * @param topic   主题
     * @param key     业务键
     * @param payload 消息体
     */
    private void safeDispatch(String topic, String key, Object payload) {
        try {
            dispatch(topic, key, payload);
        } catch (RuntimeException ex) {
            log.error("local bus async dispatch failed topic={}", topic, ex);
        }
    }

    /**
     * 创建单个分片的有界执行器。
     *
     * @param shard 分片号
     * @return 执行器
     */
    private ExecutorService newOrderedExecutor(int shard) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_QUEUE_PER_SHARD), factory("local-bus-order-" + shard));
        // 队列满时显式记日志并丢弃，不能用 CallerRunsPolicy：调用线程直接执行会破坏分片串行语义
        executor.setRejectedExecutionHandler((runnable, pool) -> log.error("local bus ordered queue full shard={}, message dropped", shard));
        return executor;
    }

    /**
     * 关闭全部线程池，避免上下文重启后残留线程与队列。
     */
    @PreDestroy
    public void shutdown() {
        delayScheduler.shutdownNow();
        orderedExecutors.values().forEach(ExecutorService::shutdownNow);
        orderedExecutors.clear();
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
