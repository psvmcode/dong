package com.dong.lab.redpacket.enums;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;
/**
 * 红包类型。决定红包金额在领取人之间的分配规则。
 */
@Getter
@AllArgsConstructor

public enum RedPacketType {

    /**
     * 固定金额红包，每个领取人获得相同金额。
     */
    FIXED(1),

    /**
     * 随机金额红包，领取人在总金额的范围内随机获得不同金额。
     */
    RANDOM(2);

    /**
     * 类型编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取红包类型枚举。
     *
     * @param code 类型编码
     * @return 红包类型枚举
     */
    public static RedPacketType of(int code) {
        for (RedPacketType type : values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown red packet type " + code);
    }

}
