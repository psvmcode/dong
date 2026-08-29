package com.dong.lab.seckill.enums;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SeckillOrderStatus {

    PENDING_PAYMENT(0),

    PAID(1),

    CANCELLED(2);

    private final int code;

    public static SeckillOrderStatus of(int code) {
        for (SeckillOrderStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown seckill order status " + code);
    }

}
