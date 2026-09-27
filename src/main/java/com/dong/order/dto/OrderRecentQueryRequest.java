package com.dong.order.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 最近订单查询请求。条数上限用查询类上限而不是批量上限，
 * 因为这里要组装返回对象，比单纯写库更吃内存。
 */
public class OrderRecentQueryRequest {

    /**
     * 返回条数。
     */
    @Min(1)
    @Max(Constants.MAX_QUERY_LIMIT)
    private int limit = Constants.DEFAULT_PAGE_SIZE;

    /**
     * 获取返回条数。
     *
     * @return 返回条数
     */
    public int getLimit() {
        return limit;
    }

    /**
     * 设置返回条数。
     *
     * @param limit 返回条数
     */
    public void setLimit(int limit) {
        this.limit = limit;
    }

}
