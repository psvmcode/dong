package com.dong.classic.entity;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * 地理位置记录。
 *
 * <p>Redis GEO 的底层是 ZSet，把经纬度编码成 score 后做范围查询，
 * 「附近的人」「附近的门店」这类需求用它非常合适。
 *
 * <p>但坐标本身是业务资产，不应该只存在于缓存里。
 * 落库后既能持久保存，也能在 Redis 数据丢失后重新灌回 GEO 集合。
 */
@Data

public class ClassicGeoPlace {

    /**
     * 主键
     */
    private Long id;

    /**
     * 城市标识，用于分城市建立 GEO 集合
     */
    private String city;

    /**
     * 成员标识，如门店或车辆编号
     */
    private String member;

    /**
     * 经度
     */
    private Double longitude;

    /**
     * 纬度
     */
    private Double latitude;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
