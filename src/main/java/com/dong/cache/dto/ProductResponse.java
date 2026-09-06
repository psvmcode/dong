package com.dong.cache.dto;

import com.dong.cache.entity.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品响应 DTO。
 */
public class ProductResponse {

    /**
     * 唯一标识 id。
     */
    private Long id;

    /**
     * 名称。
     */
    private String name;

    /**
     * 商品分类。
     */
    private String category;

    /**
     * 商品价格。
     */
    private BigDecimal price;

    /**
     * 库存数量。
     */
    private Integer stock;

    /**
     * 状态。
     */
    private String status;

    /**
     * 最后更新时间。
     */
    private LocalDateTime updateTime;

    /**
     * 从实体转换为 DTO。
     *
     * @param product 商品实体
     * @return 商品响应 DTO
     */
    public static ProductResponse from(Product product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setName(product.getName());
        response.setCategory(product.getCategory());
        response.setPrice(product.getPrice());
        response.setStock(product.getStock());
        response.setStatus(product.getStatus() == null ? null : product.getStatus().name());
        response.setUpdateTime(product.getUpdateTime());
        return response;
    }

    /**
     * 获取唯一标识 id。
     *
     * @return 唯一标识 id
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置唯一标识 id。
     *
     * @param id 唯一标识 id
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取名称。
     *
     * @return 名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置名称。
     *
     * @param name 名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取商品分类。
     *
     * @return 商品分类
     */
    public String getCategory() {
        return category;
    }

    /**
     * 设置商品分类。
     *
     * @param category 商品分类
     */
    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * 获取商品价格。
     *
     * @return 商品价格
     */
    public BigDecimal getPrice() {
        return price;
    }

    /**
     * 设置商品价格。
     *
     * @param price 商品价格
     */
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    /**
     * 获取库存数量。
     *
     * @return 库存数量
     */
    public Integer getStock() {
        return stock;
    }

    /**
     * 设置库存数量。
     *
     * @param stock 库存数量
     */
    public void setStock(Integer stock) {
        this.stock = stock;
    }

    /**
     * 获取状态。
     *
     * @return 状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置状态。
     *
     * @param status 状态
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 获取最后更新时间。
     *
     * @return 最后更新时间
     */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    /**
     * 设置最后更新时间。
     *
     * @param updateTime 最后更新时间
     */
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

}
