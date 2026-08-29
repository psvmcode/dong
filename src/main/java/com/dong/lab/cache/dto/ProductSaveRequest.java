package com.dong.lab.cache.dto;

import com.dong.lab.cache.entity.Product;
import com.dong.lab.cache.enums.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class ProductSaveRequest {

    @NotBlank
    private String name;

    private String category;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal price;

    @NotNull
    private Integer stock;

    public Product toEntity() {
        Product product = new Product();
        product.setName(name);
        product.setCategory(category == null ? "" : category);
        product.setPrice(price);
        product.setStock(stock == null ? 0 : stock);
        product.setStatus(ProductStatus.ON_SALE);
        return product;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

}
