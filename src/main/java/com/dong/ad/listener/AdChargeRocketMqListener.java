package com.dong.ad.listener;

import com.dong.ad.support.AdTopics;
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
 * 广告计费消息专用监听器。
 *
 * <p>项目里已有的 RocketMqListener 只订阅 demo-order-event，
 * 而消息是按 topic 精确路由的，所以新主题必须有自己的监听器，
 * 否则「发送成功却没人消费」，表现是事件永远停在 PENDING。
 *
 * <p>用 @ConditionalOnProperty 跟随 RocketMQ 开关：
 * 关闭时容器里连模板都没有，注册监听器会让应用启动失败。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "dong.rocketmq", name = "enabled", havingValue = "true")
@RocketMQMessageListener(topic = AdTopics.CHARGE, consumerGroup = "dong-ad-consumer", messageModel = MessageModel.CLUSTERING)
public class AdChargeRocketMqListener implements RocketMQListener<MessageExt> {

    /**
     * handlers，按 topic 路由的消息处理器列表。
     */
    private final List<MessageHandler> handlers;

    /**
     * 构造监听器。
     *
     * @param handlers 消息处理器列表
     */
    public AdChargeRocketMqListener(List<MessageHandler> handlers) {
        this.handlers = handlers;
    }

    /**
     * 接收计费消息并路由给同主题的处理器。
     *
     * @param message RocketMQ 消息
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
                throw new IllegalStateException("ad charge handler failed topic=" + topic + " handler=" + handler.getClass().getSimpleName());
            }
        }
        log.info("ad charge message received key={} topic={}", key, topic);
    }

}
