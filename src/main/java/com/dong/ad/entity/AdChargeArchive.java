package com.dong.ad.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 计费流水归档行。字段刻意与在线流水同构，
 * 这样历史追溯可以直接读归档表，不需要两套解析逻辑。
 */
@Data
public class AdChargeArchive {

    /**
     * 主键
     */
    private Long id;

    /**
     * 事件幂等键
     */
    private String eventId;

    /**
     * 投放链路 id
     */
    private String traceId;

    /**
     * 原归属日期
     */
    private LocalDate statDate;

    /**
     * 计划 id
     */
    private Long campaignId;

    /**
     * 广告主 id
     */
    private Long advertiserId;

    /**
     * 原事件类型
     */
    private Integer eventType;

    /**
     * 原金额，单位分
     */
    private Long amountCents;

    /**
     * 原流水状态
     */
    private Integer status;

    /**
     * 原预算闸门路径
     */
    private String chargeMode;

    /**
     * 归档落库时间
     */
    private LocalDateTime archivedAt;

    /**
     * 原流水创建时间
     */
    private LocalDateTime createTime;

}
