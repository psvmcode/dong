package com.dong.agent.enums;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工具危险等级。判定「有没有副作用」时最容易漏的是间接副作用：
 * 一个看起来只是跑实验的工具，可能真会往数据库打几千次请求。
 */
@Getter
@AllArgsConstructor
public enum ToolRisk {

    /**
     * 只读，无副作用，可直接执行。
     */
    READ_ONLY(1),

    /**
     * 有副作用，会写库、改缓存或产生外部请求，需要用户确认。
     */
    SIDE_EFFECT(2);

    /**
     * 等级编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取危险等级枚举。
     *
     * @param code 等级编码
     * @return 危险等级枚举
     */
    public static ToolRisk of(int code) {
        for (ToolRisk risk : values()) {
            if (risk.getCode() == code) {
                return risk;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown tool risk " + code);
    }

    /**
     * 判断是否需要在执行前让用户确认。
     *
     * @return true 表示需要确认
     */
    public boolean needConfirm() {
        return this == SIDE_EFFECT;
    }

}
