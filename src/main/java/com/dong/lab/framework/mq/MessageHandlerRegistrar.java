package com.dong.lab.framework.mq;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageHandlerRegistrar {

    private final LocalMessageBus localMessageBus;

    private final List<MessageHandler> handlers;

    @PostConstruct
    public void register() {
        handlers.forEach(localMessageBus::register);
        log.info("{} message handler(s) registered on the local bus", handlers.size());
    }

}
