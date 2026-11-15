package com.dong.ad.dto;

import com.dong.ad.entity.AdCampaign;
import lombok.Data;

import java.time.LocalDate;

/**
 * 推广计划响应。todayChargeCents 来自按天分行的日汇总，不是计划表字段。
 */
@Data
public class CampaignResponse {

    /**
     * 主键
     */
    private Long id;

    /**
     * 计划编号
     */
    private String campaignNo;

    /**
     * 广告主 id
     */
    private Long advertiserId;

    /**
     * 名称
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
     * 累计已花费，单位分
     */
    private Long spentTotalCents;

    /**
     * 今日已花费，单位分
     */
    private Long todayChargeCents;

    /**
     * 投放开始日期
     */
    private LocalDate startDate;

    /**
     * 投放结束日期
     */
    private LocalDate endDate;

    /**
     * 可投放时段
     */
    private String deliveryHours;

    /**
     * 状态名称
     */
    private String status;

    /**
     * 乐观锁版本号
     */
    private Integer version;

    /**
     * 由实体组装响应。
     *
     * @param entity          计划实体
     * @param todayChargeCents 今日已花费，单位分
     * @return 响应对象
     */
    public static CampaignResponse from(AdCampaign entity, long todayChargeCents) {
        CampaignResponse response = new CampaignResponse();
        response.setId(entity.getId());
        response.setCampaignNo(entity.getCampaignNo());
        response.setAdvertiserId(entity.getAdvertiserId());
        response.setName(entity.getName());
        response.setDailyBudgetCents(entity.getDailyBudgetCents());
        response.setTotalBudgetCents(entity.getTotalBudgetCents());
        response.setSpentTotalCents(entity.getSpentTotalCents());
        response.setTodayChargeCents(todayChargeCents);
        response.setStartDate(entity.getStartDate());
        response.setEndDate(entity.getEndDate());
        response.setDeliveryHours(entity.getDeliveryHours());
        response.setStatus(entity.getStatus().name());
        response.setVersion(entity.getVersion());
        return response;
    }

}
