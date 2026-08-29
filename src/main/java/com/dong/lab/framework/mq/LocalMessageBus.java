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

@Slf4j
@Component
public class LocalMessageBus implements MessageProducer {

    private final Map<String, List<MessageHandler>> handlers = new ConcurrentHashMap<>();

    private final ScheduledExecutorService delayScheduler = Executors.newScheduledThreadPool(2, factory("local-bus-delay"));

    private final Map<Integer, ExecutorService> orderedExecutors = new ConcurrentHashMap<>();

    private final AtomicLong dispatched = new AtomicLong();

    public void register(MessageHandler handler) {
        handlers.computeIfAbsent(handler.topic(), topic -> new CopyOnWriteArrayList<>()).add(handler);
        log.info("handler {} registered on topic {}", handler.getClass().getSimpleName(), handler.topic());
    }

    @Override
    public void send(String topic, String key, Object payload) {
        dispatch(topic, key, payload);
    }

    @Override
    public void sendDelayed(String topic, String key, Object payload, Duration delay) {
        delayScheduler.schedule(() -> dispatch(topic, key, payload), delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void sendOrdered(String topic, String key, Object payload, String shardingKey) {
        int shard = Math.floorMod(shardingKey == null ? 0 : shardingKey.hashCode(), 4);
        orderedExecutors.computeIfAbsent(shard, index -> Executors.newSingleThreadExecutor(factory("local-bus-order-" + index)))
                .submit(() -> dispatch(topic, key, payload));
    }

    @Override
    public String name() {
        return "local";
    }

    public long dispatchedCount() {
        return dispatched.get();
    }

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

    private static ThreadFactory factory(String prefix) {
        AtomicLong counter = new AtomicLong();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

}
