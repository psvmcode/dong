package com.dong.mq.listener;

import com.dong.framework.mq.MessageHandler;
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
@ConditionalOnProperty(prefix = "dong.rocketmq", name = "enabled", havingValue = "true")
@RocketMQMessageListener(topic = "demo-order-event", consumerGroup = "dong-consumer", messageModel = MessageModel.CLUSTERING)
/**
 * RocketMqListener。
 */ public class RocketMqListener implements RocketMQListener<MessageExt> {

    /**
     * handlers，按 topic 路由的消息处理器列表。
     */
    private final List<MessageHandler> handlers;

    public RocketMqListener(List<MessageHandler> handlers) {
        this.handlers = handlers;
    }

    /**
     * onMessage。
     */
    @Override
    public void onMessage(MessageExt message) {
        byte[] raw = message.getBody();
        String body = raw == null ? "" : new String(raw, StandardCharsets.UTF_8);
        String topic = message.getTopic();
        String key = message.getKeys() == null ? String.valueOf(message.getMsgId()) : message.getKeys();
        List<MessageHandler> matched = handlers.stream().filter(handler -> handler.topic().equals(topic)).toList();
        for (MessageHandler handler : matched) {
            if (!handler.handle(key, body)) {
                // 返回 false 说明连死信都没落成功，必须让容器重投，否则消息被静默 ACK
                throw new IllegalStateException("rocketmq handler failed topic=" + topic + " handler=" + handler.getClass().getSimpleName());
            }
        }
        log.info("rocketmq message received key={} topic={}", key, topic);
    }

}
