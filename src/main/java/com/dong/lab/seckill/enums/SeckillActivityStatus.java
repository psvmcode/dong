package com.dong.lab.seckill.enums;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SeckillActivityStatus {

    DRAFT(0),

    PREPARED(1),

    ONLINE(2),

    FINISHED(3);

    private final int code;

    public static SeckillActivityStatus of(int code) {
        for (SeckillActivityStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown seckill activity status " + code);
    }

}
