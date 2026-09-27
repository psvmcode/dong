package com.dong.social.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 关注关系查询请求。判断关注、求共同关注这类接口都只关心两个用户 id。
 */
public class FollowQueryRequest {

    /**
     * 发起关注的用户 id。
     */
    @NotNull
    private Long followerId;

    /**
     * 被关注的用户 id。
     */
    @NotNull
    private Long followeeId;

    /**
     * 获取发起关注的用户 id。
     *
     * @return 用户 id
     */
    public Long getFollowerId() {
        return followerId;
    }

    /**
     * 设置发起关注的用户 id。
     *
     * @param followerId 用户 id
     */
    public void setFollowerId(Long followerId) {
        this.followerId = followerId;
    }

    /**
     * 获取被关注的用户 id。
     *
     * @return 用户 id
     */
    public Long getFolloweeId() {
        return followeeId;
    }

    /**
     * 设置被关注的用户 id。
     *
     * @param followeeId 用户 id
     */
    public void setFolloweeId(Long followeeId) {
        this.followeeId = followeeId;
    }

}
