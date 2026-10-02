package com.dong.agent.entity;

import com.dong.agent.enums.ToolRisk;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 工具调用记录。与消息分开存，是因为要按工具维度统计耗时与失败率；
 * 失败也记，否则无法量化「被吞掉的那些失败」。
 */
@Data
public class AgentToolCall {

    /**
     * 主键
     */
    private Long id;

    /**
     * 所属运行号
     */
    private String runNo;

    /**
     * 所属会话号
     */
    private String sessionNo;

    /**
     * 第几步调用的
     */
    private Integer stepNo;

    /**
     * 工具名
     */
    private String toolName;

    /**
     * 入参 JSON
     */
    private String arguments;

    /**
     * 返回结果，失败时为空
     */
    private String result;

    /**
     * 结果：1 成功 0 失败
     */
    private Integer status;

    /**
     * 失败原因，成功时为空
     */
    private String errorMessage;

    /**
     * 危险等级
     */
    private ToolRisk risk;

    /**
     * 耗时，单位毫秒
     */
    private Integer elapsedMillis;

    /**
     * 调用时间
     */
    private LocalDateTime createTime;

}
