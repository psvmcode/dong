package com.dong.agent.entity;

import com.dong.agent.enums.MessageRole;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 消息。assistant 与 tool 一并记录，是轨迹可回放的前提——
 * 只存用户与最终答案的话，事后完全无法还原模型当时看到了什么。
 */
@Data
public class AgentMessage {

    /**
     * 主键
     */
    private Long id;

    /**
     * 所属会话号
     */
    private String sessionNo;

    /**
     * 所属运行号，系统消息为空
     */
    private String runNo;

    /**
     * 会话内序号，用于按序回放
     */
    private Integer seq;

    /**
     * 消息角色
     */
    private MessageRole role;

    /**
     * 消息内容，工具消息存工具返回的原始结果
     */
    private String content;

    /**
     * 工具名，仅 tool 消息有值
     */
    private String toolName;

    /**
     * 工具调用 id，回填消息时据此匹配
     */
    private String toolCallId;

    /**
     * 内容是否被截断：1 是 0 否
     */
    private Integer truncated;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
