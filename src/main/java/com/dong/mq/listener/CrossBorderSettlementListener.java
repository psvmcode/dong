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

/**
 * 跨境清算消息监听器。RocketMQ 的订阅关系挂在注解上，
 * 每个 topic 需要一个独立的监听器实例，路由逻辑与演示监听器一致：
 * 按 topic 找到对应的 MessageHandler 交给它处理。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "dong.rocketmq", name = "enabled", havingValue = "true")
@RocketMQMessageListener(topic = "cross-border-settlement", consumerGroup = "dong-crossborder-consumer", messageModel = MessageModel.CLUSTERING)
/**
 * CrossBorderSettlementListener。
 */ public class CrossBorderSettlementListener implements RocketMQListener<MessageExt> {

    /**
     * handlers，按 topic 路由的消息处理器列表。
     */
    private final List<MessageHandler> handlers;

    public CrossBorderSettlementListener(List<MessageHandler> handlers) {
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
                throw new IllegalStateException("settlement handler failed topic=" + topic + " handler=" + handler.getClass().getSimpleName());
            }
        }
        log.info("cross border settlement message received key={} topic={}", key, topic);
    }

}
