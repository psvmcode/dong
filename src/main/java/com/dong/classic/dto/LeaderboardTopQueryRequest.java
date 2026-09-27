package com.dong.classic.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 榜单前 N 名查询请求。
 */
public class LeaderboardTopQueryRequest extends LeaderboardQueryRequest {

    /**
     * 取前多少名。
     */
    @Min(1)
    @Max(Constants.MAX_QUERY_LIMIT)
    private int size = 10;

    /**
     * 获取取前多少名。
     *
     * @return 条数
     */
    public int getSize() {
        return size;
    }

    /**
     * 设置取前多少名。
     *
     * @param size 条数
     */
    public void setSize(int size) {
        this.size = size;
    }

}
