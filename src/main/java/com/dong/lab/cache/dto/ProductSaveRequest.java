package com.dong.lab.cache.dto;

import com.dong.lab.cache.entity.Product;
import com.dong.lab.cache.enums.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 商品保存请求。
 */
public class ProductSaveRequest {

    /**
     * 名称。
     */
    @NotBlank
    private String name;

    /**
     * 商品分类。
     */
    private String category;

    /**
     * 商品价格。
     */
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal price;

    /**
     * 库存数量。
     */
    @NotNull
    private Integer stock;

    /**
     * 转换为商品实体。
     *
     * @return 商品实体
     */
    public Product toEntity() {
        Product product = new Product();
        product.setName(name);
        product.setCategory(category == null ? "" : category);
        product.setPrice(price);
        product.setStock(stock == null ? 0 : stock);
        product.setStatus(ProductStatus.ON_SALE);
        return product;
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

}
