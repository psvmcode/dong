package com.dong.crossborder.dto;

import com.dong.crossborder.entity.AccountEvent;
import lombok.Data;

import java.time.LocalDateTime;
/**
 * 账户事件响应。返回给运营与审计人员，
 * 用于回答「谁在什么时间因为什么冻结或解冻了这个账户」。
 */
@Data

public class AccountEventResponse {

    /**
     * 事件记录唯一标识。
     */
    private Long id;

    /**
     * 账户编号，说明这条事件属于哪个账户。
     */
    private String accountNo;

    /**
     * 事件类型，例如冻结、解冻、限额调整等。
     */
    private String eventType;

    /**
     * 操作原因，监管回访与审计追溯时必须能还原当时依据。
     */
    private String reason;

    /**
     * 操作人，所有账户状态变更必须落实到具体责任人。
     */
    private String operator;

    /**
     * 事件发生时间。
     */
    private LocalDateTime createTime;

    /**
     * 从实体转换为 DTO。
     */
    public static AccountEventResponse from(AccountEvent entity) {
        AccountEventResponse response = new AccountEventResponse();
        response.setId(entity.getId());
        response.setAccountNo(entity.getAccountNo());
        response.setEventType(entity.getEventType() == null ? "" : entity.getEventType().name());
        response.setReason(entity.getReason());
        response.setOperator(entity.getOperator());
        response.setCreateTime(entity.getCreateTime());
        return response;
    }

}
