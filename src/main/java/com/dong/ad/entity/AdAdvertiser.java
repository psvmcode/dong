package com.dong.ad.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 广告主账户。
 *
 * <p>账户余额与计划预算是两道独立的闸门：预算控制「今天最多花多少」，
 * 余额控制「账上到底有没有钱」。只卡预算时，余额耗尽的广告主仍会持续产生
 * 无法结算的投放，最后只能靠人工冲账。
 */
@Data
public class AdAdvertiser {

    /**
     * 主键
     */
    private Long id;

    /**
     * 广告主编号
     */
    private String advertiserNo;

    /**
     * 广告主名称
     */
    private String name;

    /**
     * 账户可用余额，单位分
     */
    private Long balanceCents;

    /**
     * 状态，与 AdSwitchStatus 编码对齐：0 冻结 1 正常
     */
    private Integer status;

    /**
     * 乐观锁版本号
     */
    private Integer version;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
