package com.dong.ad.dto;

import com.dong.ad.entity.AdDailyStat;
import lombok.Data;

import java.time.LocalDate;

/**
 * 投放日报表行。比率字段只用于展示，计算都用整数计数与分为单位的金额。
 */
@Data
public class DailyReportResponse {

    /**
     * 统计日期
     */
    private LocalDate statDate;

    /**
     * 计划 id
     */
    private Long campaignId;

    /**
     * 日预算，单位分
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
     * 被护栏挡下的金额，单位分
     */
    private Long rejectedCents;

    /**
     * 被护栏挡下的笔数
     */
    private Long rejectedCount;

    /**
     * 点击率，点击数除以曝光数
     */
    private double ctr;

    /**
     * 平均点击成本，单位分
     */
    private double avgCpcCents;

    /**
     * 日预算使用率
     */
    private double budgetUsage;

    /**
     * 是否出现超投，已计费金额大于日预算
     */
    private boolean overspend;

    /**
     * 由日汇总实体组装响应。
     *
     * @param entity 日汇总实体
     * @return 响应对象
     */
    public static DailyReportResponse from(AdDailyStat entity) {
        DailyReportResponse response = new DailyReportResponse();
        response.setStatDate(entity.getStatDate());
        response.setCampaignId(entity.getCampaignId());
        response.setBudgetCents(entity.getBudgetCents());
        response.setImpressions(entity.getImpressions());
        response.setClicks(entity.getClicks());
        response.setChargeCents(entity.getChargeCents());
        response.setRejectedCents(entity.getRejectedCents());
        response.setRejectedCount(entity.getRejectedCount());
        response.setCtr(entity.getImpressions() == 0L ? 0D : (double) entity.getClicks() / entity.getImpressions());
        response.setAvgCpcCents(entity.getClicks() == 0L ? 0D : (double) entity.getChargeCents() / entity.getClicks());
        response.setBudgetUsage(entity.getBudgetCents() == 0L ? 0D : (double) entity.getChargeCents() / entity.getBudgetCents());
        response.setOverspend(entity.getChargeCents() > entity.getBudgetCents());
        return response;
    }

}
