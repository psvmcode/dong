package com.dong.order.entity;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * 订单状态流转日志。成功与失败都记录，包括被守卫拦下和并发冲突，
 * 这是并发实验能否被量化验证的关键：看日志条数而不是看最终状态。
 */
@Data

public class OrderTransitionLog {

    /**
     * 主键
     */
    private Long id;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 迁移前状态编码
     */
    private Integer fromStatus;

    /**
     * 迁移后状态编码，被拦下时与迁移前相同
     */
    private Integer toStatus;

    /**
     * 触发的事件名
     */
    private String event;

    /**
     * 结果，1 表示推进成功，0 表示被拒绝
     */
    private Integer result;

    /**
     * 拒绝原因，成功时为空
     */
    private String reason;

    /**
     * 操作人或来源标识
     */
    private String operator;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
