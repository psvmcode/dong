package com.dong.crossborder.enums;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
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

    /**
     * 冻结账户，账户余额在此期间不可用。
     */
    FREEZE(1),

    /**
     * 解冻账户，恢复账户的正常可用状态。
     */
    UNFREEZE(2);

    /**
     * 事件类型编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取账户事件类型枚举。
     *
     * @param code 事件类型编码
     * @return 账户事件类型枚举
     */
    public static AccountEventType of(int code) {
        for (AccountEventType type : values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown account event type " + code);
    }

}
