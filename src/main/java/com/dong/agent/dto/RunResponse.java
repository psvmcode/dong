package com.dong.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 运行响应。finishReason 与 status 是两个维度：
 * 被闸门终止的运行状态也是已完成，只有结束原因能说清它是怎么停的。
 */
@Data
public class RunResponse {

    /**
     * 运行号
     */
    private String runNo;

    /**
     * 会话号
     */
    private String sessionNo;

    /**
     * 状态：1 运行中 2 已完成 3 失败 4 已取消 5 等待确认
     */
    private Integer status;

    /**
     * 结束原因
     */
    private String finishReason;

    /**
     * 最终回答
     */
    private String answer;

    /**
     * 步数
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
     * 失败原因，正常时为空
     */
    private String errorMessage;

    /**
     * 挂起等待确认的工具调用 JSON，仅等待确认状态有值
     */
    private String pendingCalls;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
