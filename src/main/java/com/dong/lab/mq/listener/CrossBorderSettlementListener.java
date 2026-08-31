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

/**
 * 跨境清算消息监听器。RocketMQ 的订阅关系挂在注解上，
 * 每个 topic 需要一个独立的监听器实例，路由逻辑与演示监听器一致：
 * 按 topic 找到对应的 MessageHandler 交给它处理。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "lab.rocketmq", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = "cross-border-settlement",
        consumerGroup = "dong-lab-crossborder-consumer",
        messageModel = MessageModel.CLUSTERING
)
public class CrossBorderSettlementListener implements RocketMQListener<MessageExt> {

    private final List<MessageHandler> handlers;

    public CrossBorderSettlementListener(List<MessageHandler> handlers) {
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
        log.info("cross border settlement message received key={} topic={}", key, topic);
    }

}
