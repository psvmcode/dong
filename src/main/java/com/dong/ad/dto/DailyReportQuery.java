package com.dong.ad.dto;

import com.dong.common.constant.Constants;
import com.dong.common.result.PageQuery;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 投放日报表查询条件。日期范围不传默认查最近 7 天，
 * 跨度由服务层按 Constants.MAX_REPORT_DAYS 封顶，避免一次扫半年数据。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DailyReportQuery extends PageQuery {

    /**
     * 计划 id，可选，不传查全部计划。
     */
    @Positive
    private Long campaignId;

    /**
     * 起始日期，包含。
     */
    private LocalDate dateFrom;

    /**
     * 结束日期，包含。
     */
    private LocalDate dateTo;

    /**
     * 报表跨度上限，供 Controller 展示与校验复用。
     */
    public static final int MAX_DAYS = Constants.MAX_REPORT_DAYS;

}
