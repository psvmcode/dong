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

    ACTIVE(1),

    FROZEN(2);

    private final int code;

    public static AccountStatus of(int code) {
        for (AccountStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown account status " + code);
    }

    public boolean isActive() {
        return this == ACTIVE;
    }

    public boolean isFrozen() {
        return this == FROZEN;
    }

}
