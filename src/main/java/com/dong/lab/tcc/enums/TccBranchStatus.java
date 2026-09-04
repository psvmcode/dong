package com.dong.lab.tcc.enums;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;
/**
 * TCC 分支事务状态。记录每个分支在 Try-Confirm-Cancel 三阶段中的状态。
 */
@Getter
@AllArgsConstructor

public enum TccBranchStatus {

    /**
     * 已尝试，资源已被预留。
     */
    TRIED(1),

    /**
     * 已确认，预留资源已正式提交。
     */
    CONFIRMED(2),

    /**
     * 已取消，预留资源已被释放。
     */
    CANCELLED(3);

    /**
     * 分支事务状态编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取 TCC 分支事务状态枚举。
     *
     * @param code 状态编码
     * @return TCC 分支事务状态枚举
     */
    public static TccBranchStatus of(int code) {
        for (TccBranchStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown tcc branch status " + code);
    }

}
