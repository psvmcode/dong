package com.dong.lab.tcc.entity;

import lombok.Data;

import java.time.LocalDateTime;

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
