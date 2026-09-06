package com.dong.search.dto;

/**
 * ProductSearchRequest。
 */
public class ProductSearchRequest {

    /**
     * keyword。
     */
    private String keyword;

    /**
     * category。
     */
    private String category;

    /**
     * minPrice。
     */
    private Double minPrice;

    /**
     * maxPrice。
     */
    private Double maxPrice;

    /**
     * 1。
     */
    private int pageNum = 1;

    /**
     * 20。
     */
    private int pageSize = 20;

    /**
     * getKeyword。
     */
    public String getKeyword() {
        return keyword;
    }

    /**
     * setKeyword。
     */
    public void setKeyword(String keyword) {
        this.keyword = keyword;
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
     * getMinPrice。
     */
    public Double getMinPrice() {
        return minPrice;
    }

    /**
     * setMinPrice。
     */
    public void setMinPrice(Double minPrice) {
        this.minPrice = minPrice;
    }

    /**
     * getMaxPrice。
     */
    public Double getMaxPrice() {
        return maxPrice;
    }

    /**
     * setMaxPrice。
     */
    public void setMaxPrice(Double maxPrice) {
        this.maxPrice = maxPrice;
    }

    /**
     * getPageNum。
     */
    public int getPageNum() {
        return pageNum;
    }

    /**
     * setPageNum。
     */
    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    /**
     * getPageSize。
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * setPageSize。
     */
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

}
