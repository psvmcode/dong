package com.dong.lab.crossborder.enums;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 账户事件类型。冻结与解冻都必须落库留痕：
 * 监管检查时会追问「谁在什么时间因为什么冻结了这个账户」，
 * 只有状态字段没有事件记录是无法回答的。
 */
@Getter
@AllArgsConstructor
public enum AccountEventType {

    FREEZE(1),

    UNFREEZE(2);

    private final int code;

    public static AccountEventType of(int code) {
        for (AccountEventType type : values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown account event type " + code);
    }

}
