package com.dong.lab.replica.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserAccount {

    /**
     * 主键
     */
    private Long id;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 余额，单位分
     */
    private Long balance;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
