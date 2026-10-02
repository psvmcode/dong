package com.dong.agent.support.llm;

import com.dong.agent.enums.MessageRole;

import java.util.List;

/**
 * 对话消息。字段覆盖 OpenAI 协议里一条消息的全部形态：
 * 普通文本、带工具调用的助手消息、工具结果消息。
 *
 * @param role       消息角色
 * @param content    正文，助手消息可以为空（只带工具调用时）
 * @param toolCallId 工具调用 id，仅 tool 消息有值
 * @param toolName   工具名，仅 tool 消息有值
 * @param toolCalls  工具调用列表，仅 assistant 消息有值
 */
public record ChatMessage(MessageRole role, String content, String toolCallId, String toolName, List<ToolCall> toolCalls) {

    /**
     * 构造系统消息。
     *
     * @param content 正文
     * @return 消息
     */
    public static ChatMessage system(String content) {
        return new ChatMessage(MessageRole.SYSTEM, content, "", "", List.of());
    }

    /**
     * 构造用户消息。
     *
     * @param content 正文
     * @return 消息
     */
    public static ChatMessage user(String content) {
        return new ChatMessage(MessageRole.USER, content, "", "", List.of());
    }

    /**
     * 构造助手消息。
     *
     * @param content   正文，只带工具调用时为空
     * @param toolCalls 工具调用列表
     * @return 消息
     */
    public static ChatMessage assistant(String content, List<ToolCall> toolCalls) {
        return new ChatMessage(MessageRole.ASSISTANT, content == null ? "" : content, "", "",
                toolCalls == null ? List.of() : toolCalls);
    }

    /**
     * 构造工具结果消息。成功与失败都用它，失败时 content 里写失败原因。
     *
     * @param toolCallId 工具调用 id，回填时据此匹配
     * @param toolName   工具名
     * @param content    结果内容
     * @return 消息
     */
    public static ChatMessage tool(String toolCallId, String toolName, String content) {
        return new ChatMessage(MessageRole.TOOL, content, toolCallId, toolName, List.of());
    }

}
