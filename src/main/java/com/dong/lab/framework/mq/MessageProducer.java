package com.dong.lab.framework.mq;

import java.time.Duration;

public interface MessageProducer {

    void send(String topic, String key, Object payload);

    void sendDelayed(String topic, String key, Object payload, Duration delay);

    void sendOrdered(String topic, String key, Object payload, String shardingKey);

    String name();

}
