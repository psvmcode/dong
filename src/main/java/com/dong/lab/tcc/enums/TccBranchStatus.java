package com.dong.lab.tcc.enums;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TccBranchStatus {

    TRIED(1),

    CONFIRMED(2),

    CANCELLED(3);

    private final int code;

    public static TccBranchStatus of(int code) {
        for (TccBranchStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown tcc branch status " + code);
    }

}
