package com.dong.ad.enums;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 二值开关状态，供广告主账户与广告单元复用。
 *
 * <p>这类状态只有「能不能用」两种取值，为它单独建一个 TypeHandler 收益很低，
 * 所以实体里存 Integer，业务判断用 {@code of(code)} 反解，落库仍然是 int。
 */
@Getter
@AllArgsConstructor
public enum AdSwitchStatus {

    /**
     * 关闭：广告主冻结或单元暂停。
     */
    OFF(0),

    /**
     * 开启：广告主正常或单元启用。
     */
    ON(1);

    /**
     * 状态编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取开关状态枚举。
     *
     * @param code 状态编码
     * @return 开关状态枚举
     */
    public static AdSwitchStatus of(int code) {
        for (AdSwitchStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown ad switch status " + code);
    }

}
