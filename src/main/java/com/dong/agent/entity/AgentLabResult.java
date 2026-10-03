package com.dong.agent.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对照组实验结果。固定列覆盖通用指标，各实验的特有指标放 detail。
 *
 * <p>provider 字段必须留着：真实模型有随机性，
 * 混在一起对比会得出错误结论，实验数据默认都来自 mock。
 */
@Data
public class AgentLabResult {

    /**
     * 主键
     */
    private Long id;

    /**
     * 实验编号，如 E2
     */
    private String experiment;

    /**
     * 模式，如 serial parallel
     */
    private String mode;

    /**
     * 第几轮
     */
    private Integer round;

    /**
     * 模型提供方：mock openai
     */
    private String provider;

    /**
     * 是否达成预期：1 是 0 否
     */
    private Integer success;

    /**
     * 步数
     */
    private Integer steps;

    /**
     * 工具调用次数
     */
    private Integer toolCalls;

    /**
     * 提示 token
     */
    private Integer promptTokens;

    /**
     * 生成 token
     */
    private Integer completionTokens;

    /**
     * 耗时，单位毫秒
     */
    private Integer elapsedMillis;

    /**
     * 实验特有指标的 JSON
     */
    private String detail;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
