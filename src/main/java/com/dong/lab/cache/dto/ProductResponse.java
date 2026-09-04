package com.dong.lab.cache.dto;

import com.dong.lab.cache.entity.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ProductResponse。
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
     * category。
     */
    private String category;

    /**
     * price。
     */
    private BigDecimal price;

    /**
     * stock。
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
     * getId。
     */
    public Long getId() {
        return id;
    }

    /**
     * setId。
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * getName。
     */
    public String getName() {
        return name;
    }

    /**
     * setName。
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * getCategory。
     */
    public String getCategory() {
        return category;
    }

    /**
     * setCategory。
     */
    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * getPrice。
     */
    public BigDecimal getPrice() {
        return price;
    }

    /**
     * setPrice。
     */
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    /**
     * getStock。
     */
    public Integer getStock() {
        return stock;
    }

    /**
     * setStock。
     */
    public void setStock(Integer stock) {
        this.stock = stock;
    }

    /**
     * getStatus。
     */
    public String getStatus() {
        return status;
    }

    /**
     * setStatus。
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * getUpdateTime。
     */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    /**
     * setUpdateTime。
     */
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

}
