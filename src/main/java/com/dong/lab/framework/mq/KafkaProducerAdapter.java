package com.dong.lab.framework.mq;

import com.dong.lab.common.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lab.kafka", name = "enabled", havingValue = "true")
public class KafkaProducerAdapter implements MessageProducer {

    public static final String NOT_BEFORE_HEADER = "lab-not-before";

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public void send(String topic, String key, Object payload) {
        send(topic, key, payload, null, 0L);
    }

    @Override
    public void sendDelayed(String topic, String key, Object payload, Duration delay) {
        send(topic, key, payload, null, System.currentTimeMillis() + delay.toMillis());
    }

    @Override
    public void sendOrdered(String topic, String key, Object payload, String shardingKey) {
        send(topic, key, payload, shardingKey, 0L);
    }

    @Override
    public String name() {
        return "kafka";
    }

    private void send(String topic, String key, Object payload, String shardingKey, long notBefore) {
        String body = payload instanceof String text ? text : JsonUtils.toJson(payload);
        MessageBuilder<String> builder = MessageBuilder.withPayload(body)
                .setHeader(KafkaHeaders.TOPIC, topic)
                .setHeader(KafkaHeaders.KEY, key);
        if (shardingKey != null) {
            builder.setHeader(KafkaHeaders.PARTITION, partitionOf(shardingKey, partitionCount(topic)));
        }
        if (notBefore > 0) {
            builder.setHeader(NOT_BEFORE_HEADER, notBefore);
        }

        Message<String> message = builder.build();
        kafkaTemplate.send(message).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("kafka send failed topic={} key={}", topic, key, ex);
            }
        });
    }

    private int partitionOf(String shardingKey, int partitions) {
        if (partitions <= 1) {
            return 0;
        }
        return Math.floorMod(shardingKey.hashCode(), partitions);
    }

    private int partitionCount(String topic) {
        try {
            var partitions = kafkaTemplate.partitionsFor(topic);
            return partitions == null ? 1 : Math.max(1, partitions.size());
        } catch (Exception ex) {
            log.warn("cannot read partition count of {}, falling back to 1", topic);
            return 1;
        }
    }

}
