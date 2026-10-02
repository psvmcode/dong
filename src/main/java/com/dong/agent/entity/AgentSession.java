package com.dong.agent.entity;

import com.dong.agent.enums.SessionStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 会话。只承载上下文：一次执行的过程在 AgentRun 与 AgentMessage 里，
 * 会话表里只有累计计数与摘要。
 */
@Data
public class AgentSession {

    /**
     * 主键
     */
    private Long id;

    /**
     * 会话号
     */
    private String sessionNo;

    /**
     * 会话标题，首轮由模型生成，失败则截取首句
     */
    private String title;

    /**
     * 使用的模型标识
     */
    private String model;

    /**
     * 会话状态
     */
    private SessionStatus status;

    /**
     * 累计消息数，含工具消息
     */
    private Integer messageCount;

    /**
     * 累计运行次数
     */
    private Integer runCount;

    /**
     * 窗口之外的历史摘要，生成失败时为空
     */
    private String summary;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
