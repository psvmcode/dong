package com.dong.ad.entity;

import com.dong.ad.enums.AdCampaignStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 推广计划。
 *
 * <p>刻意不存「今日已花费」：跨天重置是个竞态窗口，重置任务晚一秒就会把
 * 新一天的花费算进旧一天。今日花费按 stat_date 落在 ad_daily_stat，
 * 日切只是换一行，不需要任何定时任务。
 */
@Data
public class AdCampaign {

    /**
     * 主键
     */
    private Long id;

    /**
     * 推广计划编号
     */
    private String campaignNo;

    /**
     * 所属广告主 id
     */
    private Long advertiserId;

    /**
     * 计划名称
     */
    private String name;

    /**
     * 日预算，单位分
     */
    private Long dailyBudgetCents;

    /**
     * 总预算，单位分
     */
    private Long totalBudgetCents;

    /**
     * 累计已计费金额，单位分
     */
    private Long spentTotalCents;

    /**
     * 投放开始日期
     */
    private LocalDate startDate;

    /**
     * 投放结束日期
     */
    private LocalDate endDate;

    /**
     * 可投放时段，24 位 0/1 字符串
     */
    private String deliveryHours;

    /**
     * 生命周期状态
     */
    private AdCampaignStatus status;

    /**
     * 乐观锁版本号
     */
    private Integer version;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
