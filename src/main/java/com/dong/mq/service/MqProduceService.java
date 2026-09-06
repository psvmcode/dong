package com.dong.mq.service;

import java.time.Duration;

/**
 * 消息发送。业务代码只依赖这一层，
 * 具体走本地总线、RocketMQ 还是 Kafka 由 MqFacade 按配置路由，切换不需要改代码。
 */
public interface MqProduceService {

    /**
     * 发送普通消息。
     */
    void send(String topic, String key, String payload);

    /**
     * 发送延迟消息。三种传输实现机制完全不同：
     * RocketMQ 只有十八个固定延迟等级，Kafka 靠消费端暂存，本地总线用定时调度。
     * 精度要求高时不能依赖 RocketMQ 的延迟等级。
     */
    void sendDelayed(String topic, String key, String payload, Duration delay);

    /**
     * 发送顺序消息。相同 shardingKey 进入同一队列或分区，从而按序消费。
     * 注意 RocketMQ 的 syncSendOrderly 会重建 Message 导致 keys 丢失，
     * 需要保留业务标识时必须用原生 send 或做兜底。
     */
    void sendOrdered(String topic, String key, String payload, String shardingKey);

    /**
     * 批量发送。
     */
    void sendBatch(String topic, String keyPrefix, int count);

    /**
     * 查看当前生效的传输实现。
     */
    java.util.Map<String, Object> status();

}
