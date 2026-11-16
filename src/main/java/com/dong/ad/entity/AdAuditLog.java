package com.dong.ad.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 配置变更审计。预算与状态都是钱相关的操作，
 * 只保留当前值无法回答「谁在什么时候把日预算改大了一百倍」。
 */
@Data
public class AdAuditLog {

    /**
     * 主键
     */
    private Long id;

    /**
     * 操作人，接口暂无登录态时写 system
     */
    private String operator;

    /**
     * 动作名称
     */
    private String action;

    /**
     * 目标类型
     */
    private String targetType;

    /**
     * 目标记录 id
     */
    private Long targetId;

    /**
     * 变更前关键字段快照
     */
    private String beforeJson;

    /**
     * 变更后关键字段快照
     */
    private String afterJson;

    /**
     * 操作原因或驳回理由
     */
    private String reason;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
