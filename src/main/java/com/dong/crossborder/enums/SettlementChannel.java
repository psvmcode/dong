package com.dong.crossborder.enums;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
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

    /**
     * SWIFT 国际电汇，覆盖面广但经代理行中转，时效慢、费用高。
     */
    SWIFT(1),

    /**
     * 人民币跨境支付系统，人民币清算时效更快、成本更低。
     */
    CIPS(2),

    /**
     * 收款国本地清算网络，成本最低但覆盖范围受限于本地接入。
     */
    LOCAL(3);

    /**
     * 渠道编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取清算渠道枚举。
     *
     * @param code 渠道编码
     * @return 清算渠道枚举
     */
    public static SettlementChannel of(int code) {
        for (SettlementChannel channel : values()) {
            if (channel.getCode() == code) {
                return channel;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown settlement channel " + code);
    }

}
