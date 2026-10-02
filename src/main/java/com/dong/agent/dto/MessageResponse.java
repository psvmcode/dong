package com.dong.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息响应。工具消息也在里面，回放时才能还原模型当时看到了什么。
 */
@Data
public class MessageResponse {

    /**
     * 会话内序号
     */
    private Integer seq;

    /**
     * 角色：system user assistant tool
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 工具名，仅 tool 消息有值
     */
    private String toolName;

    /**
     * 是否被截断
     */
    private Boolean truncated;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
