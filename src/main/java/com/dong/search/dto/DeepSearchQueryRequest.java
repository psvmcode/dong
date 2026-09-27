package com.dong.search.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 深分页检索请求。after 是上一页返回的游标，第一页不传。
 */
public class DeepSearchQueryRequest {

    /**
     * 排序方式。深分页必须按唯一键组合排序，游标才有确定的含义。
     */
    @Pattern(regexp = ProductSearchRequest.SORT_PATTERN)
    private String sort = ProductSearchRequest.SORT_PRICE_ASC;

    /**
     * 上一页返回的游标，第一页不传。
     */
    @Size(max = 128)
    private String after;

    /**
     * 每页条数。
     */
    @Min(1)
    @Max(Constants.MAX_PAGE_SIZE)
    private int size = Constants.DEFAULT_PAGE_SIZE;

    /**
     * 获取排序方式。
     *
     * @return 排序方式
     */
    public String getSort() {
        return sort;
    }

    /**
     * 设置排序方式。
     *
     * @param sort 排序方式
     */
    public void setSort(String sort) {
        this.sort = sort;
    }

    /**
     * 获取游标。
     *
     * @return 游标
     */
    public String getAfter() {
        return after;
    }

    /**
     * 设置游标。
     *
     * @param after 游标
     */
    public void setAfter(String after) {
        this.after = after;
    }

    /**
     * 获取每页条数。
     *
     * @return 每页条数
     */
    public int getSize() {
        return size;
    }

    /**
     * 设置每页条数。
     *
     * @param size 每页条数
     */
    public void setSize(int size) {
        this.size = size;
    }

}
