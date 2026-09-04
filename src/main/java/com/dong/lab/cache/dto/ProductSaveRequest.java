package com.dong.lab.cache.dto;

import com.dong.lab.cache.entity.Product;
import com.dong.lab.cache.enums.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * ProductSaveRequest。
 */
public class ProductSaveRequest {

    @NotBlank
    /**
     * 名称。
     */
    private String name;

    /**
     * category。
     */
    private String category;

    @NotNull
    @DecimalMin("0.01")
    /**
     * price。
     */
    private BigDecimal price;

    @NotNull
    /**
     * stock。
     */
    private Integer stock;

    /**
     * toEntity。
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

}
