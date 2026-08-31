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

    OPEN(1),

    CLOSED(2),

    SETTLED(3);

    private final int code;

    public static SettlementStatus of(int code) {
        for (SettlementStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown settlement status " + code);
    }

}
