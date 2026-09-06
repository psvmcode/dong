package com.dong.crossborder.enums;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;
/**
 * 汇率报价状态。锁汇是跨境支付的核心概念：
 * 报价在有效期内按锁定汇率成交，过期自动失效需要重新询价。
 * 用 AVAILABLE → LOCKED → USED 的流转保证一笔报价只会被一笔汇款使用。
 */
@Getter
@AllArgsConstructor

public enum FxQuoteStatus {

    /**
     * 可用，报价尚未被锁定，可被客户选择。
     */
    AVAILABLE(1),

    /**
     * 已锁定，报价与客户订单绑定，等待成交。
     */
    LOCKED(2),

    /**
     * 已使用，报价已被某笔汇款消耗，不可再次使用。
     */
    USED(3),

    /**
     * 已过期，超出有效时间后报价自动失效，需重新询价。
     */
    EXPIRED(4);

    /**
     * 报价状态编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取汇率报价状态枚举。
     *
     * @param code 报价状态编码
     * @return 汇率报价状态枚举
     */
    public static FxQuoteStatus of(int code) {
        for (FxQuoteStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown fx quote status " + code);
    }

}
