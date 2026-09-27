package com.dong.social.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 单用户维度的社交查询请求。关注列表、粉丝列表、计数、关系总览共用，
 * 它们的入参都只是一个用户 id。
 */
public class SocialUserQueryRequest {

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
