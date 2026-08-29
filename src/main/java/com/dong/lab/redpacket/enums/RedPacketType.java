package com.dong.lab.redpacket.enums;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RedPacketType {

    FIXED(1),

    RANDOM(2);

    private final int code;

    public static RedPacketType of(int code) {
        for (RedPacketType type : values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown red packet type " + code);
    }

}
