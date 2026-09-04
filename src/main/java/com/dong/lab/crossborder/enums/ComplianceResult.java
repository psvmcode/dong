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

    /**
     * 通过，合规检查未命中任何风险点。
     */
    PASS(1),

    /**
     * 拒绝，命中不可接受的风险，交易无法继续。
     */
    REJECT(2),

    /**
     * 需人工复核，规则无法自动决策，需人工介入。
     */
    MANUAL_REVIEW(3);

    /**
     * 结果编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取合规结果枚举。
     *
     * @param code 结果编码
     * @return 合规结果枚举
     */
    public static ComplianceResult of(int code) {
        for (ComplianceResult result : values()) {
            if (result.getCode() == code) {
                return result;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown compliance result " + code);
    }

}
