package com.dong.lab.redpacket.enums;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RedPacketStatus {

    CREATED(0),

    DISTRIBUTING(1),

    FINISHED(2),

    EXPIRED(3);

    private final int code;

    public static RedPacketStatus of(int code) {
        for (RedPacketStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown red packet status " + code);
    }

}
