package com.dong.lab.mq.listener;

import com.dong.lab.framework.mq.MessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "lab.rocketmq", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = "demo-order-event",
        consumerGroup = "dong-lab-consumer",
        messageModel = MessageModel.CLUSTERING
)
public class RocketMqListener implements RocketMQListener<MessageExt> {

    private final List<MessageHandler> handlers;

    public RocketMqListener(List<MessageHandler> handlers) {
        this.handlers = handlers;
    }

    @Override
    public void onMessage(MessageExt message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        String topic = message.getTopic();
        String key = message.getKeys() == null ? String.valueOf(message.getMsgId()) : message.getKeys();

        handlers.stream()
                .filter(handler -> handler.topic().equals(topic))
                .forEach(handler -> handler.handle(key, body));

        log.info("rocketmq message received key={} topic={}", key, topic);
    }

}
