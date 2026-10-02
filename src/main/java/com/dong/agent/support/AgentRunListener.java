package com.dong.agent.support;

import com.dong.agent.support.llm.ToolCall;
import com.dong.agent.support.tool.ToolResult;

import java.util.List;

/**
 * 运行回调。引擎只负责编排，落库与推流都由监听器的实现决定。
 *
 * <p>分开的意义：同一套循环既能给同步接口用（只要最终结果），
 * 也能给 SSE 用（逐步推给页面），还能给对照实验用（只统计不落业务数据）。
 */
public interface AgentRunListener {

    /**
     * 运行开始。
     *
     * @param runNo     运行号
     * @param sessionNo 会话号
     * @param toolCount 本次可用的工具数
     */
    void onStart(String runNo, String sessionNo, int toolCount);

    /**
     * 模型流式输出的一段正文。
     *
     * @param delta 文本片段
     */
    void onMessageDelta(String delta);

    /**
     * 模型输出了一轮内容，可能同时带工具调用。正文与工具调用可以共存，
     * 都要记下来，否则回放时看不出它当时是怎么想的。
     *
     * @param step      第几步
     * @param content   正文，只带工具调用时为空
     * @param toolCalls 本次发起的工具调用
     */
    void onAssistantMessage(int step, String content, List<ToolCall> toolCalls);

    /**
     * 即将执行一个工具。
     *
     * @param step      第几步
     * @param toolName  工具名
     * @param arguments 入参 JSON
     */
    void onToolCall(int step, String toolName, String arguments);

    /**
     * 工具执行完毕，成功与失败都会回调。
     *
     * <p>必须把工具名与调用 id 一起带出来：落库时要靠 tool_call_id 与 assistant 消息对应，
     * 只给结果的话，回放时就分不清这条结果属于哪次调用。
     *
     * @param step       第几步
     * @param toolName   工具名
     * @param toolCallId 调用 id
     * @param result     执行结果
     */
    void onToolResult(int step, String toolName, String toolCallId, ToolResult result);

    /**
     * 运行结束，含被闸门终止的情形。
     *
     * @param outcome 运行结局
     */
    void onFinish(RunOutcome outcome);

}
