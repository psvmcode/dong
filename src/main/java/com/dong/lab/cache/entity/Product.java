package com.dong.lab.cache.entity;

import com.dong.lab.cache.enums.ProductStatus;
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
     * 商品状态，1 上架 0 下架
     */
    private ProductStatus status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
