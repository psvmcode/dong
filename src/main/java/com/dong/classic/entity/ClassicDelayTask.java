package com.dong.classic.entity;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * 延迟任务记录。
 *
 * <p>Redis 延迟队列使用简单、延迟精度尚可，但它没有重试也没有持久化保证：
 * 进程重启、Redis 故障都可能导致任务静默丢失，而且无从查证。
 *
 * <p>落库之后，每一条延迟任务都有编号、状态与预计触发时间，
 * 既能追踪「任务到底有没有被消费」，也能靠重试次数字段做补偿重投。
 */
@Data

public class ClassicDelayTask {

    /**
     * 主键
     */
    private Long id;

    /**
     * 任务编号，投递时生成，便于追踪
     */
    private String taskNo;

    /**
     * 任务内容
     */
    private String payload;

    /**
     * 状态：1 待投递 2 已投递 3 已消费 4 已取消
     */
    private Integer status;

    /**
     * 预计触发时间
     */
    private LocalDateTime expectTime;

    /**
     * 实际消费时间，未消费为空
     */
    private LocalDateTime actualTime;

    /**
     * 重投次数，Redis 队列无可靠保证，靠它做补偿
     */
    private Integer retryCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
