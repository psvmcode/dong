package com.dong.replica.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 账户查询请求。按用户 id 查询与主从一致性检查共用同一个入参。
 */
public class AccountQueryRequest {

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
