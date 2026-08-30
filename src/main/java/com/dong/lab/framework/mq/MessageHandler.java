package com.dong.lab.framework.mq;

/**
 * 消息处理器。所有传输实现共用同一套业务处理逻辑，
 * 因此切换消息中间件不需要改动处理代码。
 */
public interface MessageHandler {

    /**
     * 该处理器关心的主题。
     */
    String topic();

    /**
     * 处理消息，返回 false 表示是重复投递已被幂等拦截。
     */
    boolean handle(String key, String payload);

}
