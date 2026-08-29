package com.dong.lab.framework.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Primary
@Component
public class MqFacade implements MessageProducer {

    private final LocalMessageBus localMessageBus;

    private final ObjectProvider<RocketMqProducer> rocketMqProducer;

    private final ObjectProvider<KafkaProducerAdapter> kafkaProducerAdapter;

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

    @Override
    public void send(String topic, String key, Object payload) {
        resolve().send(topic, key, payload);
    }

    @Override
    public void sendDelayed(String topic, String key, Object payload, Duration delay) {
        resolve().sendDelayed(topic, key, payload, delay);
    }

    @Override
    public void sendOrdered(String topic, String key, Object payload, String shardingKey) {
        resolve().sendOrdered(topic, key, payload, shardingKey);
    }

    @Override
    public String name() {
        return resolve().name();
    }

    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("active", active);
        status.put("local", true);
        status.put("rocketmq", rocketMqProducer.getIfAvailable() != null);
        status.put("kafka", kafkaProducerAdapter.getIfAvailable() != null);
        return status;
    }

    private MessageProducer resolve() {
        return switch (active.toLowerCase()) {
            case "rocketmq" -> require(rocketMqProducer.getIfAvailable(), "rocketmq");
            case "kafka" -> require(kafkaProducerAdapter.getIfAvailable(), "kafka");
            default -> localMessageBus;
        };
    }

    private MessageProducer require(MessageProducer producer, String name) {
        if (producer == null) {
            throw new IllegalStateException(
                    "lab.mq.active=" + name + " but lab." + name + ".enabled is false, check application.yml");
        }
        return producer;
    }

}
