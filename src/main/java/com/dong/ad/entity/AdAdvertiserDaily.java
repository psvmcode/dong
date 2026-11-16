package com.dong.ad.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 广告主账户日汇总。
 *
 * <p>计划预算再怎么精细，多个计划同时投放时仍可能把同一个账户花穿。
 * 账户日限额是最后一道总门，按天分行避免跨日重置的竞态。
 */
@Data
public class AdAdvertiserDaily {

    /**
     * 主键
     */
    private Long id;

    /**
     * 统计日期
     */
    private LocalDate statDate;

    /**
     * 广告主 id
     */
    private Long advertiserId;

    /**
     * 账户日限额快照，单位分，0 表示不限
     */
    private Long dailyLimitCents;

    /**
     * 当日已计费金额，单位分
     */
    private Long chargeCents;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
