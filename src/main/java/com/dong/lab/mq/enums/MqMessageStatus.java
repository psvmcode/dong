package com.dong.lab.mq.enums;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MqMessageStatus {

    CONSUMED(0),

    DEAD_LETTERED(1);

    private final int code;

    public static MqMessageStatus of(int code) {
        for (MqMessageStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown mq message status " + code);
    }

}
