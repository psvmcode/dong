package com.dong.ad.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 调整计划预算与投放时段请求。只传要改的字段，其余保持不变。
 *
 * <p>调大日预算会同步刷新当天日汇总的预算快照，
 * 否则条件扣减仍在用旧快照，新预算当天根本花不出去。
 */
@Data
public class CampaignUpdateRequest {

    /**
     * 计划 id。
     */
    @NotNull
    @Positive
    private Long id;

    /**
     * 新的日预算，单位分。
     */
    @Min(1)
    @Max(Constants.MAX_MONEY_CENTS)
    private Long dailyBudgetCents;

    /**
     * 新的总预算，单位分。
     */
    @Min(1)
    @Max(Constants.MAX_MONEY_CENTS)
    private Long totalBudgetCents;

    /**
     * 新的结束日期。
     */
    private LocalDate endDate;

    /**
     * 新的可投放时段，24 位 0/1 字符串。
     */
    @Size(min = 24, max = 24)
    private String deliveryHours;

}
