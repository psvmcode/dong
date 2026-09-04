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
/**
 * Kafka 发送适配器。
 *
 * <p>与 RocketMQ 的两个关键差异：
 * Kafka 没有原生延迟消息，这里靠自定义头记录生效时间，由消费端暂存后再处理；
 * 顺序消息靠分区键，相同 shardingKey 的消息写入同一分区从而保证分区内有序。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lab.kafka", name = "enabled", havingValue = "true")

public class KafkaProducerAdapter implements MessageProducer {

    // Kafka 无原生延迟消息，用这个头记录生效时间戳，消费端据此暂存
    public static final String NOT_BEFORE_HEADER = "lab-not-before";

    /**
     * Kafka 模板。
     */
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 发送普通消息。
     *
     * @param topic   主题
     * @param key     业务键
     * @param payload 消息体
     */
    @Override
    public void send(String topic, String key, Object payload) {
        send(topic, key, payload, null, 0L);
    }

    /**
     * 延迟发送。只是打个标记，真正延迟由消费端实现，
     * 因此消息会立刻出现在分区里，只是不被处理。
     *
     * @param topic   主题
     * @param key     业务键
     * @param payload 消息体
     * @param delay   延迟时长
     */
    @Override
    public void sendDelayed(String topic, String key, Object payload, Duration delay) {
        send(topic, key, payload, null, System.currentTimeMillis() + delay.toMillis());
    }

    /**
     * 顺序发送。按 shardingKey 取模选分区，保证相同 key 进入同一分区。
     *
     * @param topic       主题
     * @param key         业务键
     * @param payload     消息体
     * @param shardingKey 分片键
     */
    @Override
    public void sendOrdered(String topic, String key, Object payload, String shardingKey) {
        send(topic, key, payload, shardingKey, 0L);
    }

    /**
     * 返回消息生产者名称。
     *
     * @return 名称
     */
    @Override
    public String name() {
        return "kafka";
    }

    /**
     * 发送消息。根据 shardingKey 计算分区，根据 notBefore 标记延迟。
     *
     * @param topic       主题
     * @param key         业务键
     * @param payload     消息体
     * @param shardingKey 分片键
     * @param notBefore   生效时间戳
     */
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

    /**
     * 按 shardingKey 计算目标分区。
     *
     * @param shardingKey 分片键
     * @param partitions  分区数
     * @return 目标分区号
     */
    private int partitionOf(String shardingKey, int partitions) {
        if (partitions <= 1) {
            return 0;
        }
        return Math.floorMod(shardingKey.hashCode(), partitions);
    }

    /**
     * 查询主题分区数量。
     *
     * @param topic 主题
     * @return 分区数
     */
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
