package com.dong.agent.support.tool;

import com.dong.agent.support.llm.ToolSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表。启动时收集容器中全部 AgentTool bean，按名字索引。
 *
 * <p>用 LinkedHashMap 而不是 HashMap：工具注入模型的顺序必须稳定，
 * 否则同一句话两次运行的 prompt 都不一样，实验就没法对比。
 *
 * <p>重名直接抛异常而不是后者覆盖前者——静默覆盖会让「明明注册了却调不到」变成玄学问题。
 */
@Slf4j
@Component
public class ToolRegistry {

    /**
     * 工具名到工具的映射，保持注册顺序。
     */
    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    /**
     * 构造注册表，收集全部工具 bean。
     *
     * @param toolBeans 容器中所有 AgentTool 实现
     */
    public ToolRegistry(List<AgentTool> toolBeans) {
        for (AgentTool tool : toolBeans) {
            AgentTool previous = tools.put(tool.name(), tool);
            if (previous != null) {
                throw new IllegalStateException("duplicate agent tool name: " + tool.name());
            }
        }
        log.info("agent tool registry ready, size={} names={}", tools.size(), tools.keySet());
    }

    /**
     * 按名字取工具。
     *
     * @param name 工具名
     * @return 工具实现，不存在时返回 null
     */
    public AgentTool get(String name) {
        return tools.get(name);
    }

    /**
     * 导出给模型的工具描述。
     *
     * @return 工具描述列表
     */
    public List<ToolSpec> specs() {
        List<ToolSpec> specs = new ArrayList<>(tools.size());
        for (AgentTool tool : tools.values()) {
            specs.add(new ToolSpec(tool.name(), tool.description(), tool.parametersSchema()));
        }
        return specs;
    }

    /**
     * 全部工具名，用于「工具不存在」时告诉模型有哪些可用。
     *
     * @return 工具名列表
     */
    public List<String> names() {
        return new ArrayList<>(tools.keySet());
    }

    /**
     * 工具总数。
     *
     * @return 工具数量
     */
    public int size() {
        return tools.size();
    }

}
