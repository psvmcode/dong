package com.dong.lab.framework.mq;

import com.dong.lab.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
/**
 * 本地消息总线。在 JVM 内完成投递，不需要任何中间件，
 * 因此默认配置下也能跑通全部消息流程。
 *
 * <p>仅用于开发验证：消息存在内存里，进程重启即丢失，也不跨节点。
 */
@Slf4j
@Component

public class LocalMessageBus implements MessageProducer {

    private final Map<String, List<MessageHandler>> handlers = new ConcurrentHashMap<>();

    private final ScheduledExecutorService delayScheduler = Executors.newScheduledThreadPool(2, factory("local-bus-delay"));

    // 顺序消息按分片交给固定的单线程执行器，同一分片串行执行从而保证顺序
    private final Map<Integer, ExecutorService> orderedExecutors = new ConcurrentHashMap<>();

    private final AtomicLong dispatched = new AtomicLong();

    /**
     * register。
     */
    public void register(MessageHandler handler) {
        handlers.computeIfAbsent(handler.topic(), topic -> new CopyOnWriteArrayList<>()).add(handler);
        log.info("handler {} registered on topic {}", handler.getClass().getSimpleName(), handler.topic());
    }

    /**
     * send。
     */
    @Override
    public void send(String topic, String key, Object payload) {
        dispatch(topic, key, payload);
    }

    /**
     * sendDelayed。
     */
    @Override
    public void sendDelayed(String topic, String key, Object payload, Duration delay) {
        delayScheduler.schedule(() -> dispatch(topic, key, payload), delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * 顺序消息。按 shardingKey 哈希到固定分片，
     * 每个分片一个单线程执行器，相同 key 因此串行执行。
     */
    @Override
    public void sendOrdered(String topic, String key, Object payload, String shardingKey) {
        int shard = Math.floorMod(shardingKey == null ? 0 : shardingKey.hashCode(), 4);
        orderedExecutors.computeIfAbsent(shard, index -> Executors.newSingleThreadExecutor(factory("local-bus-order-" + index)))
                .submit(() -> dispatch(topic, key, payload));
    }

    /**
     * name。
     */
    @Override
    public String name() {
        return "local";
    }

    /**
     * dispatchedCount。
     */
    public long dispatchedCount() {
        return dispatched.get();
    }

    /**
     * 投递。单个 handler 抛异常不能影响其他 handler，因此逐个捕获；
     * 但捕获后消息即丢失，真实 MQ 需要重试或进死信队列。
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

}
