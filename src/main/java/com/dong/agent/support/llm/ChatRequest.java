package com.dong.agent.support.llm;

import java.util.List;

/**
 * 一次模型调用的请求。
 *
 * @param messages 完整对话历史
 * @param tools    本次可用的工具描述
 */
public record ChatRequest(List<ChatMessage> messages, List<ToolSpec> tools) {
}
