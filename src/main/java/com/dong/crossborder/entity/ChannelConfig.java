package com.dong.crossborder.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 清算渠道配置。
 *
 * <p>渠道的时效、单笔上限与费率都是会变的运营参数：
 * 代理行调价、监管调整额度、某条线路临时不可用，都会触发调整。
 * 写死在代码里意味着每次调整都要发版，落库后才能在线调整。
 *
 * <p>停用的渠道不参与路由打分，这是渠道故障时的熔断手段——
 * 与其让汇款一直失败，不如把流量切到还能用的渠道。
 */
@Data

public class ChannelConfig {

    /**
     * 主键
     */
    private Long id;

    /**
     * 清算渠道：1 SWIFT 2 CIPS 3 本地清算
     */
    private Integer channel;

    /**
     * 预计到账分钟数，路由评分里的时效成本
     */
    private Long etaMinutes;

    /**
     * 单笔金额上限，超过该额度的汇款不能走这个渠道
     */
    private BigDecimal perTxLimit;

    /**
     * 固定手续费，每笔都收
     */
    private BigDecimal fixedFee;

    /**
     * 比例手续费，按汇出金额乘算
     */
    private BigDecimal rateFee;

    /**
     * 是否启用：1 启用 2 停用
     */
    private Integer enabled;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
