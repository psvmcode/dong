package com.dong.agent.support.tool;

import com.dong.agent.enums.ToolRisk;

import java.time.Duration;
import java.util.Map;

/**
 * Agent 工具。所有工具实现这个接口，由 ToolRegistry 在启动时收集。
 *
 * <p>parametersSchema 返回 JSON Schema 字符串而不是对象：
 * 不同工具的入参结构差异极大，用字符串可以让每个工具自己决定描述粒度，
 * 也便于把 schema 直接塞进协议报文而不经过一层反射转换。
 *
 * <p>invoke 内部必须自己捕获全部异常并转成失败结果，不允许向外抛：
 * 一次工具异常不该毁掉整轮运行，模型本来可以换条路走。
 */
public interface AgentTool {

    /**
     * 工具名，全局唯一，用「模块.动作」命名。
     *
     * @return 工具名
     */
    String name();

    /**
     * 给模型看的描述。写清「什么时候该用」「什么时候不该用」，
     * 比写功能说明更有效——模型缺的从来不是「能不能」，而是「该不该」。
     *
     * @return 工具描述
     */
    String description();

    /**
     * 入参的 JSON Schema。
     *
     * @return JSON Schema 字符串
     */
    String parametersSchema();

    /**
     * 危险等级，决定是否需要用户确认。
     *
     * @return 危险等级
     */
    ToolRisk risk();

    /**
     * 执行工具。入参可能是任何值，实现内部必须做硬校验。
     *
     * @param arguments 模型给出的入参
     * @return 执行结果，失败时 success 为 false 且 errorMessage 必填
     */
    ToolResult invoke(Map<String, Object> arguments);

    /**
     * 单个工具的超时，默认 15 秒。慢工具可以覆写，全量实验类工具应当缩短。
     *
     * @return 超时时长
     */
    default Duration timeout() {
        return Duration.ofSeconds(15);
    }

    /**
     * 结果字符上限，默认 8000。超出即截断并置 truncated 标记。
     *
     * @return 结果字符上限
     */
    default int maxResultChars() {
        return 8_000;
    }

}
