package com.dong.lab.crossborder.enums;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 合规检查结果。MANUAL_REVIEW 是真实系统里常见的一态：
 * 规则判不出来时挂起等人工处理，既不自动放行也不自动拒绝。
 */
@Getter
@AllArgsConstructor
public enum ComplianceResult {

    PASS(1),

    REJECT(2),

    MANUAL_REVIEW(3);

    private final int code;

    public static ComplianceResult of(int code) {
        for (ComplianceResult result : values()) {
            if (result.getCode() == code) {
                return result;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown compliance result " + code);
    }

}
