package com.dong.lab.framework.mq;

import java.time.Duration;

/**
 * 消息发送抽象。业务代码只依赖这一接口，
 * 具体走本地总线、RocketMQ 还是 Kafka 由 MqFacade 按配置路由。
 */
public interface MessageProducer {

    /**
     * 发送普通消息。
     */
    void send(String topic, String key, Object payload);

    /**
     * 发送延迟消息。三种实现机制不同，RocketMQ 只有十八个固定延迟等级，
     * Kafka 靠消费端暂存，本地总线用定时调度。
     */
    void sendDelayed(String topic, String key, Object payload, Duration delay);

    /**
     * 发送顺序消息，相同 shardingKey 的消息按发送顺序被消费。
     */
    void sendOrdered(String topic, String key, Object payload, String shardingKey);

    /**
     * 返回消息生产者名称。
     *
     * @return 名称
     */
    String name();

}
