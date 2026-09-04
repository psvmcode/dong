package com.dong.lab.replica.entity;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * 用户账户。读写分离实验场景中的核心账户，
 * 写走主库、读走从库，验证主从延迟与读写路由策略。
 */
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
