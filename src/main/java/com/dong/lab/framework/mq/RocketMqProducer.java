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

/**
 * RocketMQ 发送实现。
 *
 * <p>两个实现细节值得注意：
 * 延迟消息只有十八个固定等级，任意时长会被向上取到最近的等级，因此精度有限；
 * 顺序消息必须用原生 send 而不是 RocketMQTemplate 的 syncSendOrderly，
 * 因为后者会重建 Message 对象导致业务 keys 丢失，消费端取不到就入不了库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lab.rocketmq", name = "enabled", havingValue = "true")
public class RocketMqProducer implements MessageProducer {

    /**
     * RocketMQ 固定的十八个延迟等级，不能自定义。
     */
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

    /**
     * 延迟发送。传入时长会被向上取到最近的固定等级，
     * 因此实际生效时间可能比请求的长。
     */
    @Override
    public void sendDelayed(String topic, String key, Object payload, Duration delay) {
        sendSync(topic, key, payload, delayLevel(delay));
    }

    /**
     * 顺序发送。用原生 send 保留业务 keys，
     * 相同 shardingKey 的消息由选择器固定投递到同一队列。
     */
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
     * 按 shardingKey 哈希选队列，保证相同 key 落到同一队列，
     * 队列内先进先出因此消费也是有序的。
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
