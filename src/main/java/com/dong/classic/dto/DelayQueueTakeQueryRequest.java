package com.dong.classic.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 延迟任务取出请求。只决定一次取多少条，未到期的任务不会被返回。
 */
public class DelayQueueTakeQueryRequest {

    /**
     * 一次取出的条数。
     */
    @Min(1)
    @Max(Constants.MAX_BATCH_SIZE)
    private int limit = 10;

    /**
     * 获取一次取出的条数。
     *
     * @return 条数
     */
    public int getLimit() {
        return limit;
    }

    /**
     * 设置一次取出的条数。
     *
     * @param limit 条数
     */
    public void setLimit(int limit) {
        this.limit = limit;
    }

}
