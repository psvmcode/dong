package com.dong.lab.crossborder.entity;

import com.dong.lab.crossborder.enums.AccountEventType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 账户事件。账户冻结、解冻这类改变账户可用性的操作逐条落库，
 * 与账户状态字段互为印证：状态是「现在」，事件是「怎么走到现在的」。
 */
@Data
public class AccountEvent {

    /**
     * 主键
     */
    private Long id;

    /**
     * 事件所属账户
     */
    /**
     * 账号
     */
    private String accountNo;

    /**
     * 事件类型：冻结、解冻
     */
    /**
     * 账户事件类型，如开户、冻结、解冻
     */
    private AccountEventType eventType;

    /**
     * 操作原因，监管检查必填项
     */
    /**
     * 事件原因
     */
    private String reason;

    /**
     * 操作人，决策必须可追溯到具体的人
     */
    /**
     * 操作人
     */
    private String operator;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
