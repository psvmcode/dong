package com.dong.lab.crossborder.enums;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;
/**
 * 合规检查类型。真实的跨境汇款要依次过这几道：
 * 制裁名单筛查最优先，命中直接拒绝；
 * KYC 校验客户身份等级是否足以做该笔金额；
 * AML 检查交易模式是否可疑；
 * 限额检查累计与单笔是否在允许范围内。
 * MANUAL_REVIEW 是人工复核环节：大额交易挂起后由合规人员放行或驳回，
 * 决策结果同样要留痕，监管检查时这是必备证据。
 */
@Getter
@AllArgsConstructor

public enum ComplianceCheckType {

    /**
     * 制裁名单筛查，命中后直接拒绝，不再进入后续检查。
     */
    SANCTION(1),

    /**
     * KYC 等级校验，判断客户身份等级是否足以支持该笔交易金额。
     */
    KYC(2),

    /**
     * 反洗钱检查，识别交易模式是否可疑。
     */
    AML(3),

    /**
     * 限额检查，校验累计与单笔金额是否在允许范围内。
     */
    LIMIT(4),

    /**
     * 人工复核，大额或复杂交易挂起后由合规人员决策放行或驳回。
     */
    MANUAL_REVIEW(5);

    /**
     * 检查类型编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取合规检查类型枚举。
     *
     * @param code 检查类型编码
     * @return 合规检查类型枚举
     */
    public static ComplianceCheckType of(int code) {
        for (ComplianceCheckType type : values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown compliance check type " + code);
    }

}
