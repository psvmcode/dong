package com.dong.ad.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 计费流水，金额的唯一权威源。
 *
 * <p>stat_date 取事件归属日而不是落库日：消息积压到零点后才被消费时，
 * 用落库日会把昨天的钱算进今天，日预算与报表会同时失真。
 */
@Data
public class AdChargeRecord {

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
     * 归属日期
     */
    private LocalDate statDate;

    /**
     * 计划 id
     */
    private Long campaignId;

    /**
     * 单元 id
     */
    private Long unitId;

    /**
     * 创意 id
     */
    private Long creativeId;

    /**
     * 广告主 id
     */
    private Long advertiserId;

    /**
     * 触发计费的事件类型
     */
    private Integer eventType;

    /**
     * 实扣金额，单位分
     */
    private Long amountCents;

    /**
     * 状态，与 AdChargeStatus 编码对齐：2 已计费 3 已拒绝
     */
    private Integer status;

    /**
     * 预算闸门路径，redis 或 db
     */
    private String chargeMode;

    /**
     * 拒绝原因
     */
    private String rejectReason;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
