package com.dong.lab.tcc.enums;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TccTransactionStatus {

    TRYING(1),

    CONFIRMING(2),

    CONFIRMED(3),

    CANCELLING(4),

    CANCELLED(5);

    private final int code;

    public static TccTransactionStatus of(int code) {
        for (TccTransactionStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown tcc transaction status " + code);
    }

}
