package com.dong.lab.mq.service;

import java.time.Duration;

public interface MqProduceService {

    void send(String topic, String key, String payload);

    void sendDelayed(String topic, String key, String payload, Duration delay);

    void sendOrdered(String topic, String key, String payload, String shardingKey);

    void sendBatch(String topic, String keyPrefix, int count);

    java.util.Map<String, Object> status();

}
