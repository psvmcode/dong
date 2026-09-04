package com.dong.lab.framework.mq;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
/**
 * 把容器中所有 MessageHandler 注册到本地总线。
 *
 * <p>之所以由本地总线统一持有：切换传输实现时业务处理器无需改动，
 * RockerMQ 与 Kafka 的监听器同样会调用这些 handler。
 */
@Slf4j
@Component
@RequiredArgsConstructor

public class MessageHandlerRegistrar {

    /**
     * 本地消息总线。
     */
    private final LocalMessageBus localMessageBus;

    /**
     * 所有消息处理器。
     */
    private final List<MessageHandler> handlers;

    /**
     * 将容器中所有消息处理器注册到本地总线。
     */
    @PostConstruct
    public void register() {
        handlers.forEach(localMessageBus::register);
        log.info("{} message handler(s) registered on the local bus", handlers.size());
    }

}
