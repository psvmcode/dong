package com.dong.ad.enums;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 预算闸门路径。REDIS 用 Lua 预扣做高并发挡流，DB 直接靠条件更新串行化。
 *
 * <p>两条路径的最终正确性都由数据库条件更新保证，Redis 只是副本与加速器，
 * 因此它可以随时挂掉、随时被对账任务重建，不影响金额守恒。
 */
@Getter
@AllArgsConstructor
public enum AdChargeMode {

    /**
     * Redis Lua 预扣 + 异步落库。
     */
    REDIS(1),

    /**
     * 纯数据库条件扣减，中间件不可用时的降级路径。
     */
    DB(2);

    /**
     * 模式编码。
     */
    private final int code;

    /**
     * 落库时使用的名称，小写。
     *
     * @return 模式名称
     */
    public String text() {
        return name().toLowerCase();
    }

    /**
     * 按名称解析模式，非法值转成 1000。
     *
     * @param name 模式名称
     * @return 模式枚举
     */
    public static AdChargeMode ofName(String name) {
        for (AdChargeMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown ad charge mode " + name);
    }

    /**
     * 根据编码获取模式枚举。
     *
     * @param code 模式编码
     * @return 模式枚举
     */
    public static AdChargeMode of(int code) {
        for (AdChargeMode mode : values()) {
            if (mode.getCode() == code) {
                return mode;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown ad charge mode " + code);
    }

}
