package com.dong.social.dto;

import com.dong.common.result.PageQuery;
import jakarta.validation.constraints.NotNull;

/**
 * 拉模式时间线查询请求。拉模式要分页，所以继承分页基类，
 * 页码与每页大小的校验不用在这里重复一遍。
 */
public class TimelinePullQueryRequest extends PageQuery {

    /**
     * 用户 id。
     */
    @NotNull
    private Long userId;

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

}
