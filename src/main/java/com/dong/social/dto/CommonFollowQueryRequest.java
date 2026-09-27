package com.dong.social.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 共同关注查询请求。两个用户的关注集合求交集，顺序不影响结果。
 */
public class CommonFollowQueryRequest {

    /**
     * 第一个用户 id。
     */
    @NotNull
    private Long firstUserId;

    /**
     * 第二个用户 id。
     */
    @NotNull
    private Long secondUserId;

    /**
     * 获取第一个用户 id。
     *
     * @return 用户 id
     */
    public Long getFirstUserId() {
        return firstUserId;
    }

    /**
     * 设置第一个用户 id。
     *
     * @param firstUserId 用户 id
     */
    public void setFirstUserId(Long firstUserId) {
        this.firstUserId = firstUserId;
    }

    /**
     * 获取第二个用户 id。
     *
     * @return 用户 id
     */
    public Long getSecondUserId() {
        return secondUserId;
    }

    /**
     * 设置第二个用户 id。
     *
     * @param secondUserId 用户 id
     */
    public void setSecondUserId(Long secondUserId) {
        this.secondUserId = secondUserId;
    }

}
