package com.dong.framework.mq;

import com.dong.framework.mq.impl.KafkaProducerAdapter;
import com.dong.framework.mq.impl.LocalMessageBus;
import com.dong.framework.mq.impl.RocketMqProducer;

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
 * 由它按 dong.mq.active 把请求路由到本地总线、RocketMQ 或 Kafka，
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
     * 本地消息总线。
     */
    private final LocalMessageBus localMessageBus;

    /**
     * RocketMQ 生产者提供者。
     */
    private final ObjectProvider<RocketMqProducer> rocketMqProducer;

    /**
     * Kafka 生产者适配器提供者。
     */
    private final ObjectProvider<KafkaProducerAdapter> kafkaProducerAdapter;

    /**
     * 当前激活的消息中间件类型。
     */
    private final String active;

    /**
     * 构造消息门面。
     *
     * @param localMessageBus        本地消息总线
     * @param rocketMqProducer       RocketMQ 生产者提供者
     * @param kafkaProducerAdapter   Kafka 生产者适配器提供者
     * @param active                   激活的消息中间件类型
     */
    public MqFacade(LocalMessageBus localMessageBus,
                    ObjectProvider<RocketMqProducer> rocketMqProducer,
                    ObjectProvider<KafkaProducerAdapter> kafkaProducerAdapter,
                    @Value("${dong.mq.active:local}") String active) {
        this.localMessageBus = localMessageBus;
        this.rocketMqProducer = rocketMqProducer;
        this.kafkaProducerAdapter = kafkaProducerAdapter;
        this.active = active;
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
        resolve().send(topic, key, payload);
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
        resolve().sendDelayed(topic, key, payload, delay);
    }

    /**
     * 发送顺序消息。
     *
     * @param topic       主题
     * @param key         业务键
     * @param payload     消息体
     * @param shardingKey 分片键
     */
    @Override
    public void sendOrdered(String topic, String key, Object payload, String shardingKey) {
        resolve().sendOrdered(topic, key, payload, shardingKey);
    }

    /**
     * 返回当前消息生产者名称。
     *
     * @return 名称
     */
    @Override
    public String name() {
        return resolve().name();
    }

    /**
     * 查询当前消息中间件状态。
     *
     * @return 状态信息
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
     * 根据配置解析出实际使用的消息生产者。
     *
     * @return 消息生产者
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
                    "dong.mq.active=" + name + " but dong." + name + ".enabled is false, check application.yml");
        }
        return producer;
    }

}
