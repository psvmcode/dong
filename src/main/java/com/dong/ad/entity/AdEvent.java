package com.dong.ad.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投放事件与令牌。
 *
 * <p>event_type=0 的行是服务端签发的令牌，成交价与过期时间都在上面；
 * 曝光与点击各占一行，靠 uk_trace_type 保证同一链路同一类型只计一次，
 * 靠 uk_event_id 保证客户端带同一个幂等键重试时不会重复扣钱。
 *
 * <p>事件上冗余了计划、单元、创意与广告主 id：异步计费消息可能晚到，
 * 那时再反查令牌既多一次数据库往返，也可能查到已被清理的行。
 */
@Data
public class AdEvent {

    /**
     * 主键
     */
    private Long id;

    /**
     * 投放链路 id
     */
    private String traceId;

    /**
     * 事件幂等键
     */
    private String eventId;

    /**
     * 事件类型，与 AdEventType 编码对齐
     */
    private Integer eventType;

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
     * 触达用户 id
     */
    private Long userId;

    /**
     * 请求地域
     */
    private String region;

    /**
     * 请求设备
     */
    private String device;

    /**
     * 请求 IP
     */
    private String ip;

    /**
     * 胜出单元原始出价，单位分
     */
    private Long bidCents;

    /**
     * 广义二价成交价，单位分
     */
    private Long clearingCents;

    /**
     * 计费状态，与 AdChargeStatus 编码对齐
     */
    private Integer chargeStatus;

    /**
     * 是否有效，0 无效流量 1 有效
     */
    private Integer valid;

    /**
     * 无效或拒绝原因
     */
    private String rejectReason;

    /**
     * 预算闸门路径，redis 或 db
     */
    private String chargeMode;

    /**
     * 计费消息补投次数，用于退避与死信判定
     */
    private Integer resendCount;

    /**
     * 下次允许补投的时间，未到点不重复投递
     */
    private LocalDateTime nextResendTime;

    /**
     * 令牌过期时间，仅 REQUEST 行有值
     */
    private LocalDateTime expireTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
