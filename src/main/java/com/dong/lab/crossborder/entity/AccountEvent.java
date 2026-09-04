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
     * 事件所属账户的账号，冻结或解冻操作的对象。
     */
    private String accountNo;

    /**
     * 账户事件类型，如冻结、解冻等改变账户可用性的状态变更。
     */
    private AccountEventType eventType;

    /**
     * 操作原因，监管检查与审计追溯必填字段。
     */
    private String reason;

    /**
     * 操作人，账户状态变更必须能追溯到具体责任人。
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
