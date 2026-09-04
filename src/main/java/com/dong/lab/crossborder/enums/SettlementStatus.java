package com.dong.lab.crossborder.enums;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;
/**
 * 清算批次状态。真实跨境清算按批次走，因为渠道有清算窗口和起息时间，
 * 逐笔实时清算既不经济也不符合渠道规则。
 * OPEN 期间可继续并入汇款单，CLOSED 后等待渠道处理，SETTLED 表示已到账。
 */
@Getter
@AllArgsConstructor

public enum SettlementStatus {

    /**
     * 开放中，批次尚未关闸，可继续并入新的汇款单。
     */
    OPEN(1),

    /**
     * 已关闸，批次等待渠道清算处理。
     */
    CLOSED(2),

    /**
     * 已清算到账，批次内所有汇款完成资金交割。
     */
    SETTLED(3);

    /**
     * 批次状态编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取清算批次状态枚举。
     *
     * @param code 批次状态编码
     * @return 清算批次状态枚举
     */
    public static SettlementStatus of(int code) {
        for (SettlementStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown settlement status " + code);
    }

}
