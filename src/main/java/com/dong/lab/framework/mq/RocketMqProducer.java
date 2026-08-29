package com.dong.lab.framework.mq;

import com.dong.lab.common.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lab.rocketmq", name = "enabled", havingValue = "true")
public class RocketMqProducer implements MessageProducer {

    private static final List<Duration> DELAY_LEVELS = List.of(
            Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30),
            Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(3), Duration.ofMinutes(4),
            Duration.ofMinutes(5), Duration.ofMinutes(6), Duration.ofMinutes(7), Duration.ofMinutes(8),
            Duration.ofMinutes(9), Duration.ofMinutes(10), Duration.ofMinutes(20), Duration.ofMinutes(30),
            Duration.ofHours(1), Duration.ofHours(2));

    private final RocketMQTemplate rocketMqTemplate;

    @Override
    public void send(String topic, String key, Object payload) {
        sendSync(topic, key, payload, 0);
    }

    @Override
    public void sendDelayed(String topic, String key, Object payload, Duration delay) {
        sendSync(topic, key, payload, delayLevel(delay));
    }

    @Override
    public void sendOrdered(String topic, String key, Object payload, String shardingKey) {
        Message message = build(topic, key, payload);
        try {
            rocketMqTemplate.getProducer().send(message, new ShardingSelector(), shardingKey);
        } catch (Exception ex) {
            log.error("rocketmq ordered send failed topic={} key={}", topic, key, ex);
            throw new IllegalStateException("rocketmq ordered send failed", ex);
        }
    }

    @Override
    public String name() {
        return "rocketmq";
    }

    private void sendSync(String topic, String key, Object payload, int delayLevel) {
        Message message = build(topic, key, payload);
        if (delayLevel > 0) {
            message.setDelayTimeLevel(delayLevel);
        }
        try {
            rocketMqTemplate.getProducer().send(message);
        } catch (Exception ex) {
            log.error("rocketmq send failed topic={} key={}", topic, key, ex);
            throw new IllegalStateException("rocketmq send failed", ex);
        }
    }

    private Message build(String topic, String key, Object payload) {
        String body = payload instanceof String text ? text : JsonUtils.toJson(payload);
        Message message = new Message(topic, body.getBytes(StandardCharsets.UTF_8));
        message.setKeys(key);
        return message;
    }

    /**
     * Selects the queue by the sharding key, the same key always lands on the same queue and is
     * therefore consumed in the order it was sent.
     */
    private static final class ShardingSelector implements org.apache.rocketmq.client.producer.MessageQueueSelector {

        @Override
        public org.apache.rocketmq.common.message.MessageQueue select(
                java.util.List<org.apache.rocketmq.common.message.MessageQueue> queues,
                org.apache.rocketmq.common.message.Message message,
                Object argument) {
            int index = Math.floorMod(String.valueOf(argument).hashCode(), queues.size());
            return queues.get(index);
        }

    }

    private int delayLevel(Duration delay) {
        for (int i = 0; i < DELAY_LEVELS.size(); i++) {
            if (!DELAY_LEVELS.get(i).minus(delay).isNegative()) {
                return i + 1;
            }
        }
        return DELAY_LEVELS.size();
    }

}
