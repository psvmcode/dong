package com.dong.ad.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 创建推广计划请求。
 */
@Data
public class CampaignCreateRequest {

    /**
     * 所属广告主 id。
     */
    @NotNull
    @Positive
    private Long advertiserId;

    /**
     * 计划名称。
     */
    @NotBlank
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String name;

    /**
     * 日预算，单位分。
     */
    @NotNull
    @Min(1)
    @Max(Constants.MAX_MONEY_CENTS)
    private Long dailyBudgetCents;

    /**
     * 总预算，单位分。
     */
    @NotNull
    @Min(1)
    @Max(Constants.MAX_MONEY_CENTS)
    private Long totalBudgetCents;

    /**
     * 投放开始日期。
     */
    @NotNull
    private LocalDate startDate;

    /**
     * 投放结束日期。
     */
    @NotNull
    private LocalDate endDate;

    /**
     * 可投放时段，24 位 0/1 字符串，不传表示全天可投。
     */
    @Size(min = 24, max = 24)
    private String deliveryHours;

}
