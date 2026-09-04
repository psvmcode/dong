package com.dong.lab.mq.handler;

import com.dong.lab.framework.mq.MessageHandler;
import com.dong.lab.mq.service.MqConsumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
/**
 * DemoOrderMessageHandler。
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class DemoOrderMessageHandler implements MessageHandler {

    public static final String TOPIC = "demo-order-event";

    /**
     * mqConsumeService，业务服务层。
     */
    private final MqConsumeService mqConsumeService;

    @Override
    /**
     * topic。
     */
    public String topic() {
        return TOPIC;
    }

    @Override
    /**
     * handle。
     */
    public boolean handle(String key, String payload) {
        return mqConsumeService.consume(TOPIC, key, payload);
    }

}
