package com.dong.classic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 榜单成员查询请求。查名次与查分数都按「哪个榜的哪个成员」定位。
 */
public class LeaderboardMemberQueryRequest extends LeaderboardQueryRequest {

    /**
     * 成员标识。
     */
    @NotBlank
    @Size(max = 128)
    private String member;

    /**
     * 获取成员标识。
     *
     * @return 成员标识
     */
    public String getMember() {
        return member;
    }

    /**
     * 设置成员标识。
     *
     * @param member 成员标识
     */
    public void setMember(String member) {
        this.member = member;
    }

}
