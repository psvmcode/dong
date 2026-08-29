package com.dong.lab.tcc.enums;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TccOrderStatus {

    PENDING(1),

    CONFIRMED(2),

    CANCELLED(3);

    private final int code;

    public static TccOrderStatus of(int code) {
        for (TccOrderStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown tcc order status " + code);
    }

}
