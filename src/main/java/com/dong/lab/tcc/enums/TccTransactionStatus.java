package com.dong.lab.tcc.enums;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;
/**
 * TCC 事务状态。记录全局事务在三阶段提交中的执行状态。
 */
@Getter
@AllArgsConstructor

public enum TccTransactionStatus {

    /**
     * Try 阶段执行中，各分支资源正在预留。
     */
    TRYING(1),

    /**
     * Confirm 阶段执行中，各分支预留资源正在提交。
     */
    CONFIRMING(2),

    /**
     * 已确认，全局事务提交完成。
     */
    CONFIRMED(3),

    /**
     * Cancel 阶段执行中，各分支预留资源正在释放。
     */
    CANCELLING(4),

    /**
     * 已取消，全局事务回滚完成。
     */
    CANCELLED(5);

    /**
     * 事务状态编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取 TCC 事务状态枚举。
     *
     * @param code 状态编码
     * @return TCC 事务状态枚举
     */
    public static TccTransactionStatus of(int code) {
        for (TccTransactionStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown tcc transaction status " + code);
    }

}
