package com.dong.agent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 运行结束原因。每次运行都必须有值——
 * 排查时第一个要问的就是「它是怎么停的」，没有这个字段就只能靠猜。
 */
@Getter
@AllArgsConstructor
public enum FinishReason {

    /**
     * 模型给出最终回答，自然结束。
     */
    STOP("模型结束"),

    /**
     * 达到步数上限。
     */
    MAX_STEPS("步数耗尽"),

    /**
     * 达到工具调用次数上限。
     */
    MAX_TOOL_CALLS("工具调用次数耗尽"),

    /**
     * 达到墙钟超时。
     */
    TIMEOUT("运行超时"),

    /**
     * 达到 token 预算。
     */
    TOKEN_BUDGET("预算耗尽"),

    /**
     * 工具连续失败或重复调用。
     */
    TOOL_FAILURE("工具反复失败"),

    /**
     * 用户主动停止，或确认超时。
     */
    CANCELLED("已取消"),

    /**
     * 挂起等待用户确认有副作用的工具，不是终态。
     */
    WAITING_CONFIRM("等待确认"),

    /**
     * 引擎自身出错或模型不可用。
     */
    ERROR("运行出错");

    /**
     * 中文标签，展示给页面。
     */
    private final String label;

    /**
     * 判断是否属于正常结束。被闸门砍掉的不算成功，但也不是错误，
     * 统计时要能区分这三档。
     *
     * @return true 表示模型给出了最终回答
     */
    public boolean isStop() {
        return this == STOP;
    }

}
