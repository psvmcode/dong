package com.dong.classic.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 榜单邻域查询请求。range 是往前或往后各看多少名，
 * 上限 100 是因为这个接口一次要取两段区间，太大会拖慢 Redis。
 */
public class LeaderboardAroundQueryRequest extends LeaderboardMemberQueryRequest {

    /**
     * 前后各看多少名。
     */
    @Min(1)
    @Max(100)
    private int range = 2;

    /**
     * 获取前后各看多少名。
     *
     * @return 范围
     */
    public int getRange() {
        return range;
    }

    /**
     * 设置前后各看多少名。
     *
     * @param range 范围
     */
    public void setRange(int range) {
        this.range = range;
    }

}
