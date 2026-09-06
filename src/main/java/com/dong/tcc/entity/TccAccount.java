package com.dong.tcc.entity;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * TCC 账户。在 Try-Confirm-Cancel 分布式事务中承担账户余额的预留与扣减，
 * balance 与 frozen 配合实现资源隔离。
 */
@Data

public class TccAccount {

    /**
     * 主键
     */
    private Long id;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 可用余额
     */
    private Long balance;

    /**
     * 冻结金额，Try 阶段冻结 Confirm 扣减
     */
    private Long frozen;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
