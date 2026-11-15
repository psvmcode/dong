package com.dong.ad.enums;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 投放事件类型。REQUEST 行同时充当服务端签发的投放令牌，
 * 成交价、过期时间、触达上下文都挂在它上面，上报事件时不能由客户端改写。
 */
@Getter
@AllArgsConstructor
public enum AdEventType {

    /**
     * 投放请求，即令牌行。
     */
    REQUEST(0),

    /**
     * 曝光。
     */
    IMPRESSION(1),

    /**
     * 点击。
     */
    CLICK(2);

    /**
     * 类型编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取事件类型枚举。
     *
     * @param code 类型编码
     * @return 事件类型枚举
     */
    public static AdEventType of(int code) {
        for (AdEventType type : values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown ad event type " + code);
    }

}
