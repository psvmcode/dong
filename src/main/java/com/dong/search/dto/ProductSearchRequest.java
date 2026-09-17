package com.dong.search.dto;

/**
 * ProductSearchRequest。
 */
public class ProductSearchRequest {

    /**
     * 按相关性排序，也就是按 _score 排，默认值。
     */
    public static final String SORT_RELEVANCE = "relevance";

    /**
     * 按价格从低到高。
     */
    public static final String SORT_PRICE_ASC = "price_asc";

    /**
     * 按价格从高到低。
     */
    public static final String SORT_PRICE_DESC = "price_desc";

    /**
     * 按创建时间从新到旧。
     */
    public static final String SORT_CREATED_DESC = "created_desc";

    /**
     * 允许的排序取值，控制器用它做参数校验。
     */
    public static final String SORT_PATTERN = "relevance|price_asc|price_desc|created_desc";

    /**
     * 搜索关键字。走 multi_match，name 权重 3 倍、description 权重 1 倍，并带 fuzzy 容错。
     * 不传则不过滤，配合价格区间可以只做筛选不做全文检索。
     */
    private String keyword;

    /**
     * 分类过滤。走 term 精确匹配而不是分词，因为 category 是 keyword 类型，
     * 用 match 查 keyword 字段只会匹配到整个值。
     */
    private String category;

    /**
     * 价格下限，含边界。与 maxPrice 一起构成 range 过滤，只传一个就是单边区间。
     */
    private Double minPrice;

    /**
     * 价格上限，含边界。
     */
    private Double maxPrice;

    /**
     * 页码，从 1 开始。它参与 from 的计算：from=(pageNum-1)*pageSize，
     * 所以页码乘以页大小一旦超过 ES 的一万条上限会被直接拒绝，深翻页请走 /deep。
     */
    private int pageNum = 1;

    /**
     * 每页条数。
     */
    private int pageSize = 20;

    /**
     * 排序方式：relevance 按相关性，price_asc 与 price_desc 按价格，created_desc 按创建时间。
     *
     * <p>一旦按价格或时间排序，_score 就不再有意义（相当于关掉相关性排序），
     * 这是有代价的取舍：排序稳定了，但「最匹配」这件事没了。
     */
    private String sort = SORT_RELEVANCE;

    /**
     * 是否包含已下架商品。默认只搜在售的，下架商品对消费者不该可见。
     */
    private boolean includeOffShelf = false;

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

    /**
     * getSort。
     */
    public String getSort() {
        return sort;
    }

    /**
     * setSort。
     */
    public void setSort(String sort) {
        this.sort = sort;
    }

    /**
     * isIncludeOffShelf。
     */
    public boolean isIncludeOffShelf() {
        return includeOffShelf;
    }

    /**
     * setIncludeOffShelf。
     */
    public void setIncludeOffShelf(boolean includeOffShelf) {
        this.includeOffShelf = includeOffShelf;
    }

}
