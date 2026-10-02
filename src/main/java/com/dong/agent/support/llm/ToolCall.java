package com.dong.agent.support.llm;

/**
 * 模型发起的一次工具调用。arguments 是原始 JSON 字符串，
 * 由引擎负责解析——解析失败要回传给模型，不能在这里就抛掉。
 *
 * @param id        调用 id，模型生成，回填结果时据此匹配
 * @param name      工具名
 * @param arguments 入参 JSON 字符串
 */
public record ToolCall(String id, String name, String arguments) {
}
