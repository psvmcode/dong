package com.dong.ad.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 投放日汇总。
 *
 * <p>budget_cents 是当日日预算快照，目的是把「今日已花 + 本笔 <= 日预算」
 * 写进同一条 update 的 where，让数据库行锁完成原子判定，
 * 而不是先查后改留一个并发超投窗口。调整预算时要同步刷新当天快照。
 */
@Data
public class AdDailyStat {

    /**
     * 主键
     */
    private Long id;

    /**
     * 统计日期
     */
    private LocalDate statDate;

    /**
     * 计划 id
     */
    private Long campaignId;

    /**
     * 当日预算快照，单位分
     */
    private Long budgetCents;

    /**
     * 有效曝光数
     */
    private Long impressions;

    /**
     * 有效点击数
     */
    private Long clicks;

    /**
     * 已计费金额，单位分
     */
    private Long chargeCents;

    /**
     * 被预算或余额挡下的金额，单位分
     */
    private Long rejectedCents;

    /**
     * 被挡下的笔数
     */
    private Long rejectedCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
