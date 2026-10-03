package com.dong.agent.dto;

import lombok.Data;

/**
 * 单个实验模式的运行结果。
 */
@Data
public class LabRunItem {

    /**
     * 实验编号
     */
    private String experiment;

    /**
     * 模式
     */
    private String mode;

    /**
     * 第几轮
     */
    private Integer round;

    /**
     * 模型提供方
     */
    private String provider;

    /**
     * 运行号，便于回查轨迹
     */
    private String runNo;

    /**
     * 是否达成预期
     */
    private Boolean success;

    /**
     * 结束原因
     */
    private String finishReason;

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
     * 实验特有指标，JSON 文本
     */
    private String detail;

    /**
     * 失败原因，正常时为空
     */
    private String errorMessage;

}
