package com.dong.ad.enums;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 计费类型。决定哪一类事件真正扣钱：CPC 只在点击时计费，CPM 只在曝光时计费。
 *
 * <p>另一类事件仍然要落库，因为它要进报表的曝光数、点击数与 CTR，
 * 只是不产生计费流水。把「有效果」和「要扣钱」混为一谈是最常见的计费 bug。
 */
@Getter
@AllArgsConstructor
public enum AdBidType {

    /**
     * 按点击计费。
     */
    CPC(1),

    /**
     * 按千次曝光计费。
     */
    CPM(2);

    /**
     * 类型编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取计费类型枚举。
     *
     * @param code 类型编码
     * @return 计费类型枚举
     */
    public static AdBidType of(int code) {
        for (AdBidType type : values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown ad bid type " + code);
    }

}
