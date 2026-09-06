package com.dong.replica.service;

import com.dong.replica.entity.UserAccount;

import java.util.List;

/**
 * 第二数据源账户。数据源、会话工厂和事务管理器都与主库独立，
 * 用来演示多数据源配置以及本地事务的边界。
 */
public interface UserAccountService {

    /**
     * 创建账户。
     */
    Long create(Long userId, String username, long balance);

    /**
     * 按用户 id 查询。
     */
    UserAccount findByUserId(Long userId);

    /**
     * 查询全部账户。
     */
    List<UserAccount> findAll();

    /**
     * 转账，在同一个本地事务内扣减与增加。
     * 跨库场景无法这样保证，需要引入分布式事务。
     */
    long transfer(Long fromUserId, Long toUserId, long amount);

    /**
     * 连读两次比对结果，用于观察读写分离下的延迟。
     */
    java.util.Map<String, Object> consistencyCheck(Long userId);

}
