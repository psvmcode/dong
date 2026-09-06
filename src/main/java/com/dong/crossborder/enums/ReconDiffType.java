package com.dong.crossborder.enums;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
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

    /**
     * 长款，渠道记录金额大于本地记录金额。
     */
    LONG(1),

    /**
     * 短款，渠道记录金额小于本地记录金额。
     */
    SHORT(2),

    /**
     * 金额不一致，通常是汇率或手续费计算口径不同导致。
     */
    AMOUNT_MISMATCH(3),

    /**
     * 渠道缺失，本地有记录但渠道侧无对应记录。
     */
    MISSING_IN_CHANNEL(4),

    /**
     * 本地缺失，渠道有记录但本地侧无对应记录。
     */
    MISSING_IN_LOCAL(5);

    /**
     * 差异类型编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取对账差异类型枚举。
     *
     * @param code 差异类型编码
     * @return 对账差异类型枚举
     */
    public static ReconDiffType of(int code) {
        for (ReconDiffType type : values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown recon diff type " + code);
    }

}
