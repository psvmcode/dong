package com.dong.classic.entity;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * 限流算法对比结果。
 *
 * <p>四种算法只打一轮突发时放行数量必然相同，因为窗口内都最多放行 limit 个，
 * 区分不出算法。真正的差异在配额如何恢复，要看间隔后的第二轮。
 *
 * <p>因此这里第二轮放行数是关键字段，用 -1 表示本轮没有做第二轮。
 * 落库后可以直接对比历史上不同参数下的表现。
 */
@Data

public class ClassicRateLimitLabResult {

    /**
     * 主键
     */
    private Long id;

    /**
     * 限流业务键
     */
    private String bizKey;

    /**
     * 限流算法，如 fixed_window 固定窗口 token_bucket 令牌桶
     */
    private String algorithm;

    /**
     * 窗口内允许通过的次数
     */
    private Long limitCount;

    /**
     * 窗口时长，单位秒
     */
    private Long windowSeconds;

    /**
     * 本轮突发尝试的总次数
     */
    private Integer attempts;

    /**
     * 第一轮突发放行的次数
     */
    private Long firstBurstAllowed;

    /**
     * 第二轮突发放行的次数，-1 表示未做第二轮
     */
    private Long secondBurstAllowed;

    /**
     * 是否分布式限流：1 是 2 否
     */
    private Integer distributed;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
