package com.dong.agent.support.llm;

import java.util.List;

/**
 * 模型返回结果。content 与 toolCalls 可以共存（模型边说边调），
 * 引擎据此决定是继续循环还是结束。
 *
 * @param content          正文
 * @param toolCalls        工具调用列表，没有调用时为空列表
 * @param promptTokens     提示 token，粗估
 * @param completionTokens 生成 token，粗估
 */
public record LlmResult(String content, List<ToolCall> toolCalls, int promptTokens, int completionTokens) {

    /**
     * 是否发起了工具调用。
     *
     * @return true 表示有工具调用
     */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

}
