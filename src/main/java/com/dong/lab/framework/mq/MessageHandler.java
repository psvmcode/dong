package com.dong.lab.framework.mq;

public interface MessageHandler {

    String topic();

    boolean handle(String key, String payload);

}
