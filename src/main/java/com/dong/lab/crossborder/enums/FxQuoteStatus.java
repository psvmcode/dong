package com.dong.lab.crossborder.enums;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
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

    AVAILABLE(1),

    LOCKED(2),

    USED(3),

    EXPIRED(4);

    private final int code;

    public static FxQuoteStatus of(int code) {
        for (FxQuoteStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown fx quote status " + code);
    }

}
