package com.dong.agent.support.llm;

/**
 * 给模型看的工具描述。
 *
 * @param name              工具名
 * @param description       工具描述
 * @param parametersSchema 入参的 JSON Schema
 */
public record ToolSpec(String name, String description, String parametersSchema) {
}
