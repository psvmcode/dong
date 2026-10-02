package com.dong.agent.support;

/**
 * 运行事件的推送出口。同步接口传 null，SSE 接口传 SseEmitter 的包装，
 * 对照实验可以传一个只统计不落库的实现。
 */
@FunctionalInterface
public interface AgentEventSink {

    /**
     * 推送一个事件。声明抛出 Exception 是因为底层可能写失败的流（SSE 连接已断开），
     * 这类失败由调用方决定是记日志还是中断，不适合在接口里吞掉。
     *
     * @param event 事件名
     * @param data  事件数据
     * @throws Exception 推送失败
     */
    void send(String event, Object data) throws Exception;

}
