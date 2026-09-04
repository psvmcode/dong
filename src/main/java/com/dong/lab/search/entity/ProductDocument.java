package com.dong.lab.search.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 商品搜索文档。与 MySQL 商品表对应，用于 Elasticsearch 全文检索、
 * 聚合分析与搜索高亮演示。
 */
@Data

public class ProductDocument {

    /**
     * 文档 id，与 MySQL 主键对应
     */
    private String id;

    /**
     * 商品名称，ik_max_word 索引
     */
    private String name;

    /**
     * 分类，keyword 类型支持 terms 聚合
     */
    private String category;

    /**
     * 商品描述，ik_max_word 索引
     */
    private String description;

    /**
     * 商品价格
     */
    private BigDecimal price;

    /**
     * 库存数量
     */
    private Integer stock;

    /**
     * 商品状态
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
