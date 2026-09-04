package com.dong.lab.search.dto;

import com.dong.lab.search.entity.ProductDocument;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
/**
 * ProductSearchResponse。
 */
@Data

public class ProductSearchResponse {

    /**
     * total。
     */
    private long total;

    /**
     * pageNum。
     */
    private int pageNum;

    /**
     * pageSize。
     */
    private int pageSize;

    /**
     * list。
     */
    private List<Hit> list;

    /**
     * categoryFacets。
     */
    private Map<String, Long> categoryFacets;

    @Data
    public static class Hit {
    /**
     * 唯一标识 id。
     */
        private String id;

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
     * highlight。
     */
        private List<String> highlight;

    }

    /**
     * empty。
     */
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
