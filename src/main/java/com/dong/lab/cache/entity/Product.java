package com.dong.lab.cache.entity;

import com.dong.lab.cache.enums.ProductStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Product {

    private Long id;

    private String name;

    private String category;

    private BigDecimal price;

    private Integer stock;

    private ProductStatus status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
