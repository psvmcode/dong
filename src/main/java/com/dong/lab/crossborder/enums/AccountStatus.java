package com.dong.lab.crossborder.enums;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;
/**
 * 账户状态。冻结不是删除：账户与历史流水必须完整保留，
 * 这是反洗钱调查与司法取证的要求，解冻后账户可立即恢复使用。
 */
@Getter
@AllArgsConstructor

public enum AccountStatus {

    /**
     * 正常，账户可正常进行汇入汇出。
     */
    ACTIVE(1),

    /**
     * 冻结，账户余额不可用，需解冻后才能恢复交易。
     */
    FROZEN(2);

    /**
     * 状态编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取账户状态枚举。
     *
     * @param code 状态编码
     * @return 账户状态枚举
     */
    public static AccountStatus of(int code) {
        for (AccountStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown account status " + code);
    }

    /**
     * 判断账户是否为正常状态。
     *
     * @return true 表示正常
     */
    public boolean isActive() {
        return this == ACTIVE;
    }

    /**
     * 判断账户是否为冻结状态。
     *
     * @return true 表示冻结
     */
    public boolean isFrozen() {
        return this == FROZEN;
    }

}
