package com.dong.social.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 推模式时间线查询请求。推模式下数据已经提前写好，这里只决定读多少条。
 */
public class TimelinePushQueryRequest {

    /**
     * 用户 id。
     */
    @NotNull
    private Long userId;

    /**
     * 读取条数。
     */
    @Min(1)
    @Max(Constants.MAX_QUERY_LIMIT)
    private int size = Constants.DEFAULT_PAGE_SIZE;

    /**
     * 获取用户 id。
     *
     * @return 用户 id
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 设置用户 id。
     *
     * @param userId 用户 id
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * 获取读取条数。
     *
     * @return 读取条数
     */
    public int getSize() {
        return size;
    }

    /**
     * 设置读取条数。
     *
     * @param size 读取条数
     */
    public void setSize(int size) {
        this.size = size;
    }

}
