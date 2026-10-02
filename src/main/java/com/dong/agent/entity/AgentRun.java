package com.dong.agent.entity;

import com.dong.agent.enums.FinishReason;
import com.dong.agent.enums.RunStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 运行。一次提交的完整账本：怎么停的、花了多少步多少 token。
 *
 * <p>finish_reason 与 status 是两个维度：被闸门终止的运行 status 仍是已完成，
 * 但它不是正常答完的，只有 finish_reason 能说清这一点。
 */
@Data
public class AgentRun {

    /**
     * 主键
     */
    private Long id;

    /**
     * 运行号
     */
    private String runNo;

    /**
     * 所属会话号
     */
    private String sessionNo;

    /**
     * 幂等键，未传为 null
     */
    private String clientToken;

    /**
     * 用户输入
     */
    private String prompt;

    /**
     * 最终回答，被闸门终止时为空
     */
    private String answer;

    /**
     * 运行状态
     */
    private RunStatus status;

    /**
     * 结束原因
     */
    private FinishReason finishReason;

    /**
     * 实际执行步数
     */
    private Integer steps;

    /**
     * 工具调用次数
     */
    private Integer toolCalls;

    /**
     * 提示 token，粗估
     */
    private Integer promptTokens;

    /**
     * 生成 token，粗估
     */
    private Integer completionTokens;

    /**
     * 总耗时，单位毫秒
     */
    private Integer elapsedMillis;

    /**
     * 失败原因，成功时为空
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
