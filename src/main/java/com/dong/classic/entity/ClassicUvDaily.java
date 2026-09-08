package com.dong.classic.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
/**
 * 每日独立访客快照。
 *
 * <p>HyperLogLog 用约 12KB 就能估算上亿级别的独立访客数，
 * 代价是约 0.81% 的误差，所以它适合看趋势，不适合对账。
 *
 * <p>落库保存的是当天最后一次估算值。Redis 里的 HLL 会过期，
 * 落库后才能回答「上个月每天的 UV 分别是多少」。
 */
@Data

public class ClassicUvDaily {

    /**
     * 主键
     */
    private Long id;

    /**
     * 页面标识
     */
    private String page;

    /**
     * 统计日期
     */
    private LocalDate statDate;

    /**
     * 当日独立访客估算值
     */
    private Long uvCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间，当天重复统计会覆盖更新
     */
    private LocalDateTime updateTime;

}
