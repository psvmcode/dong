package com.dong.ad.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 广告单元。定向条件、出价、预估点击率都在这一层，
 * 一次召回对每个启用单元算出竞价值后再排序。
 */
@Data
public class AdUnit {

    /**
     * 主键
     */
    private Long id;

    /**
     * 广告单元编号
     */
    private String unitNo;

    /**
     * 所属推广计划 id
     */
    private Long campaignId;

    /**
     * 单元名称
     */
    private String name;

    /**
     * 定向条件 JSON，空数组表示不限
     */
    private String targeting;

    /**
     * 计费类型，与 AdBidType 编码对齐
     */
    private Integer bidType;

    /**
     * 出价，单位分，CPM 时为每千次曝光价格
     */
    private Long bidCents;

    /**
     * 保留价，单位分，广义二价的最小成交价
     */
    private Long floorCents;

    /**
     * 预估点击率，万分比
     */
    private Integer pctr;

    /**
     * 状态，与 AdSwitchStatus 编码对齐：0 暂停 1 启用
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
