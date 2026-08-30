package com.dong.lab.search.dto;

import com.dong.lab.search.entity.ProductDocument;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ProductSearchResponse {

    private long total;

    private int pageNum;

    private int pageSize;

    private List<Hit> list;

    private Map<String, Long> categoryFacets;

    @Data
    public static class Hit {
        private String id;

        private String name;

        private String category;

        private BigDecimal price;

        private Integer stock;

        private List<String> highlight;

    }

    public static ProductSearchResponse empty(int pageNum, int pageSize) {
        ProductSearchResponse response = new ProductSearchResponse();
        response.setTotal(0L);
        response.setPageNum(pageNum);
        response.setPageSize(pageSize);
        response.setList(List.of());
        response.setCategoryFacets(Map.of());
        return response;
    }

}
