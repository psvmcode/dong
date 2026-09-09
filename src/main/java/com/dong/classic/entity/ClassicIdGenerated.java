package com.dong.classic.entity;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * 发号器生成记录。
 *
 * <p>四种发号策略各有取舍，光靠一次跑分说明不了问题，
 * 把每次批量生成的策略、数量与耗时记下来，积累之后才能看出真实差异。
 *
 * <p>注意这里记的是批次而不是单个 id：
 * 一次生成几万个 id 却只落库一条，避免落库本身成为性能瓶颈，
 * 反而干扰「对比发号器性能」这个实验目的。
 */
@Data

public class ClassicIdGenerated {

    /**
     * 主键
     */
    private Long id;

    /**
     * 发号策略：snowflake 雪花 segment 号段 redis 自增 uuid
     */
    private String strategy;

    /**
     * 本批生成的数量
     */
    private Integer idCount;

    /**
     * 本批最后一个值，雪花与自增可直接比较大小
     */
    private String lastId;

    /**
     * 生成耗时，单位毫秒，用于横向对比四种策略的性能
     */
    private Double elapsedMillis;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
