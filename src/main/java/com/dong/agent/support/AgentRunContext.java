package com.dong.agent.support;

import com.dong.agent.support.llm.ChatMessage;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 一次运行的输入上下文。
 *
 * <p>cancelChecker 用 Supplier 而不是布尔字段：取消信号存在库里，
 * 循环每轮都要重新读一次，否则用户点了停止而循环看不到。
 */
@Data
public class AgentRunContext {

    /**
     * 运行号。
     */
    private String runNo;

    /**
     * 会话号。
     */
    private String sessionNo;

    /**
     * 用户输入。
     */
    private String prompt;

    /**
     * 进入模型的上下文：窗口内的历史消息与摘要拼成的系统消息。
     */
    private List<ChatMessage> history = new ArrayList<>();

    /**
     * 是否允许用户确认过的有副作用工具直接执行。
     */
    private boolean confirmSideEffect;

    /**
     * 取消信号查询，返回 true 表示应当立即停止。
     */
    private Supplier<Boolean> cancelChecker = () -> false;

    /**
     * 实验参数覆盖，正常运行时为 null。
     */
    private AgentRunOptions options = AgentRunOptions.empty();

}
