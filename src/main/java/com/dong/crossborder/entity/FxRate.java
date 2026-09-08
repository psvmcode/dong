package com.dong.crossborder.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 汇率牌价。
 *
 * <p>牌价以美元为桥：表里存的是「一美元兑换多少该币种」，
 * 任意两个币种之间的汇率由各自对美元的牌价推导，
 * 这样只需要维护 N 条记录就能支持 N×(N-1) 个货币对，
 * 不必维护全量货币对。
 *
 * <p>牌价落库而不是写在代码里，是因为它需要能被调整、能被审计：
 * 出问题时要能回答「这笔汇款成交时用的牌价是谁在什么时候定的」。
 */
@Data

public class FxRate {

    /**
     * 主键
     */
    private Long id;

    /**
     * 币种代码，与账户币种一致
     */
    private String currency;

    /**
     * 一美元兑换该币种的数量，7.15 表示 1 美元兑 7.15 该币种
     */
    private BigDecimal usdRate;

    /**
     * 状态：1 启用 2 停用，停用的币种不允许开户与询价
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间，调整牌价时自动刷新
     */
    private LocalDateTime updateTime;

}
