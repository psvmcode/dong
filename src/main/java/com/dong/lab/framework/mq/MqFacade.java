package com.dong.lab.framework.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
/**
 * 消息门面。业务代码只依赖 MessageProducer 接口，
 * 由它按 lab.mq.active 把请求路由到本地总线、RocketMQ 或 Kafka，
 * 因此切换消息中间件是改配置而不是改代码。
 *
 * <p>两个实现用 ObjectProvider 而非直接注入：
 * 它们各自受开关控制，关闭时容器里没有对应 bean，直接注入会启动失败。
 */
@Slf4j
@Primary
@Component

public class MqFacade implements MessageProducer {

    /**
     * localMessageBus。
     */
    private final LocalMessageBus localMessageBus;

    /**
     * rocketMqProducer。
     */
    private final ObjectProvider<RocketMqProducer> rocketMqProducer;

    /**
     * kafkaProducerAdapter。
     */
    private final ObjectProvider<KafkaProducerAdapter> kafkaProducerAdapter;

    /**
     * active。
     */
    private final String active;

    public MqFacade(LocalMessageBus localMessageBus,
                    ObjectProvider<RocketMqProducer> rocketMqProducer,
                    ObjectProvider<KafkaProducerAdapter> kafkaProducerAdapter,
                    @Value("${lab.mq.active:local}") String active) {
        this.localMessageBus = localMessageBus;
        this.rocketMqProducer = rocketMqProducer;
        this.kafkaProducerAdapter = kafkaProducerAdapter;
        this.active = active;
    }

    /**
     * send。
     */
    @Override
    public void send(String topic, String key, Object payload) {
        resolve().send(topic, key, payload);
    }

    /**
     * sendDelayed。
     */
    @Override
    public void sendDelayed(String topic, String key, Object payload, Duration delay) {
        resolve().sendDelayed(topic, key, payload, delay);
    }

    /**
     * sendOrdered。
     */
    @Override
    public void sendOrdered(String topic, String key, Object payload, String shardingKey) {
        resolve().sendOrdered(topic, key, payload, shardingKey);
    }

    /**
     * name。
     */
    @Override
    public String name() {
        return resolve().name();
    }

    /**
     * status。
     */
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("active", active);
        status.put("local", true);
        status.put("rocketmq", rocketMqProducer.getIfAvailable() != null);
        status.put("kafka", kafkaProducerAdapter.getIfAvailable() != null);
        return status;
    }

    /**
     * resolve。
     */
    private MessageProducer resolve() {
        return switch (active.toLowerCase()) {
            case "rocketmq" -> require(rocketMqProducer.getIfAvailable(), "rocketmq");
            case "kafka" -> require(kafkaProducerAdapter.getIfAvailable(), "kafka");
            default -> localMessageBus;
        };
    }

    /**
     * 配置不一致时快速失败。若允许静默回落到本地总线，
     * 消息就会在进程内空转，线上表现为消息发出去了却没人处理，很难排查。
     */
    private MessageProducer require(MessageProducer producer, String name) {
        if (producer == null) {
            throw new IllegalStateException(
                    "lab.mq.active=" + name + " but lab." + name + ".enabled is false, check application.yml");
        }
        return producer;
    }

}
