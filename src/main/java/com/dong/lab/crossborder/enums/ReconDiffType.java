package com.dong.lab.crossborder.enums;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 对账差异类型。长款指渠道记的比本地多，短款指渠道记的比本地少，
 * 这两类会直接造成资金损失，必须当日处理；
 * 金额不一致通常是汇率或手续费计算方式不同导致的，需要逐笔核对。
 */
@Getter
@AllArgsConstructor
public enum ReconDiffType {

    LONG(1),

    SHORT(2),

    AMOUNT_MISMATCH(3),

    MISSING_IN_CHANNEL(4),

    MISSING_IN_LOCAL(5);

    private final int code;

    public static ReconDiffType of(int code) {
        for (ReconDiffType type : values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown recon diff type " + code);
    }

}
