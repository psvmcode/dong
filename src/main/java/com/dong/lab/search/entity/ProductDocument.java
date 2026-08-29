package com.dong.lab.search.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductDocument {

    private String id;

    private String name;

    private String category;

    private String description;

    private BigDecimal price;

    private Integer stock;

    private String status;

    private LocalDateTime createTime;

}
