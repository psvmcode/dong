package com.dong.mq.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 消息日志查询请求。只决定读多少条，条数上限用查询类上限。
 */
public class MqLogQueryRequest {

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
