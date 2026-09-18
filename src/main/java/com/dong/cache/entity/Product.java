package com.dong.cache.entity;

import com.dong.cache.enums.ProductStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 商品。缓存实验场景的核心实体，演示缓存失效、缓存穿透、
 * 缓存击穿与 Redis 分布式锁等场景。
 */
@Data
public class Product {

    /**
     * 主键
     */
    private Long id;

    /**
     * 商品名称，IK 分词字段
     */
    private String name;

    /**
     * 商品分类，keyword 类型用于分面聚合
     */
    private String category;

    /**
     * 商品价格
     */
    private BigDecimal price;

    /**
     * 库存数量
     */
    private Integer stock;

    /**
     * 门店经度。地理检索场景用，为空表示这个商品不参与距离计算。
     */
    private Double longitude;

    /**
     * 门店纬度。地理检索场景用，为空表示这个商品不参与距离计算。
     */
    private Double latitude;

    /**
     * 商品状态，1 上架 2 已下架
     */
    private ProductStatus status;

    /**
     * 商品详情。大文本，是索引里 description 字段的来源，参与全文检索与高亮。
     */
    private String description;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
