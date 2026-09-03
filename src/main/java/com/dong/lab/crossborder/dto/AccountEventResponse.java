package com.dong.lab.crossborder.dto;

import com.dong.lab.crossborder.entity.AccountEvent;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 账户事件响应。返回给运营与审计人员，
 * 用于回答「谁在什么时间因为什么冻结或解冻了这个账户」。
 */
@Data
public class AccountEventResponse {

    private Long id;

    private String accountNo;

    private String eventType;

    private String reason;

    private String operator;

    private LocalDateTime createTime;

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
