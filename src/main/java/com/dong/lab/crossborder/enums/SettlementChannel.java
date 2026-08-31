package com.dong.lab.crossborder.enums;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 清算渠道。不同渠道的时效、成本和覆盖范围差异很大：
 * SWIFT 覆盖面最广但要经过代理行，到账慢且费用高，还可能被中间行扣费；
 * CIPS 是人民币跨境支付系统，走人民币清算时更快更便宜；
 * LOCAL 指落地到收款国的本地清算网络，成本最低但覆盖范围有限。
 */
@Getter
@AllArgsConstructor
public enum SettlementChannel {

    SWIFT(1),

    CIPS(2),

    LOCAL(3);

    private final int code;

    public static SettlementChannel of(int code) {
        for (SettlementChannel channel : values()) {
            if (channel.getCode() == code) {
                return channel;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown settlement channel " + code);
    }

}
