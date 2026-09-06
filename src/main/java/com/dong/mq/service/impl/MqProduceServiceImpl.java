package com.dong.mq.service.impl;

import com.dong.framework.mq.MessageProducer;
import com.dong.mq.service.MqProduceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
/**
 * 消息发送实现。业务代码只依赖抽象，
 * 具体走本地总线、RocketMQ 还是 Kafka 由 MqFacade 按配置路由。
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class MqProduceServiceImpl implements MqProduceService {

    /**
     * messageProducer。
     */
    private final MessageProducer messageProducer;

    /**
     * send。
     */
    @Override
    public void send(String topic, String key, String payload) {
        messageProducer.send(topic, key, payload);
        log.info("message sent topic={} key={}", topic, key);
    }

    /**
     * sendDelayed。
     */
    @Override
    public void sendDelayed(String topic, String key, String payload, Duration delay) {
        messageProducer.sendDelayed(topic, key, payload, delay);
        log.info("delayed message sent topic={} key={} delay={}ms", topic, key, delay.toMillis());
    }

    /**
     * sendOrdered。
     */
    @Override
    public void sendOrdered(String topic, String key, String payload, String shardingKey) {
        messageProducer.sendOrdered(topic, key, payload, shardingKey);
        log.info("ordered message sent topic={} key={} shardingKey={}", topic, key, shardingKey);
    }

    /**
     * sendBatch。
     */
    @Override
    public void sendBatch(String topic, String keyPrefix, int count) {
        for (int i = 1; i <= count; i++) {
            messageProducer.send(topic, keyPrefix + "-" + i, "{\"seq\":" + i + "}");
        }
        log.info("batch of {} messages sent topic={}", count, topic);
    }

    /**
     * status。
     */
    @Override
    public Map<String, Object> status() {
        if (messageProducer instanceof com.dong.framework.mq.MqFacade facade) {
            return facade.status();
        }
        return Map.of("transport", messageProducer.name());
    }

}
