package com.dong.lab.search.dto;

import lombok.Data;

@Data
public class ProductSearchRequest {

    private String keyword;

    private String category;

    private Double minPrice;

    private Double maxPrice;

    private int pageNum = 1;

    private int pageSize = 20;

}
