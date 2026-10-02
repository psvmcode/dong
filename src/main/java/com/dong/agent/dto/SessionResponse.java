package com.dong.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话响应。
 */
@Data
public class SessionResponse {

    /**
     * 会话号
     */
    private String sessionNo;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 使用的模型标识
     */
    private String model;

    /**
     * 状态：1 活跃 2 归档
     */
    private Integer status;

    /**
     * 累计消息数
     */
    private Integer messageCount;

    /**
     * 累计运行次数
     */
    private Integer runCount;

    /**
     * 窗口之外的历史摘要
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
