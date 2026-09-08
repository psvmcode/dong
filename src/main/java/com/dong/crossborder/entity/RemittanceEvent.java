package com.dong.crossborder.entity;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * 汇款单流转日志。
 *
 * <p>状态字段只回答「现在是什么状态」，这张表回答「它怎么变成这样的」。
 * 资金系统里这两个问题同等重要：客户投诉、监管检查、故障复盘，
 * 靠的都是后者。
 *
 * <p>成功与被拒绝都记。只记成功的话，就无法回答
 * 「为什么这笔钱卡了两天」这类真正需要排查的问题。
 */
@Data

public class RemittanceEvent {

    /**
     * 主键
     */
    private Long id;

    /**
     * 所属汇款单号
     */
    private String remittanceNo;

    /**
     * 变更前状态编码
     */
    private Integer fromStatus;

    /**
     * 变更后状态编码，原地打转时与变更前相同
     */
    private Integer toStatus;

    /**
     * 触发动作名，如 create、lockQuote、settle、return
     */
    private String event;

    /**
     * 结果：1 成功 0 被拒绝
     */
    private Integer result;

    /**
     * 说明，被拒绝时填原因，成功时填来源
     */
    private String reason;

    /**
     * 操作人或来源标识，人工审核时记审核人
     */
    private String operator;

    /**
     * 发生时间
     */
    private LocalDateTime createTime;

}
