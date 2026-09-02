package com.dong.lab.crossborder.enums;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 汇款单状态机。跨境汇款的状态推进是单向的，只能从前向后，
 * 失败后统一走退款而不是回退，因为资金已经划出，回退会产生在途资金。
 *
 * <p>正常链路：CREATED → QUOTE_LOCKED → FUNDS_DEBITED → SETTLING → SETTLED
 * 异常链路：任一前置状态 → COMPLIANCE_REJECTED 或 FAILED → REFUNDED
 */
@Getter
@AllArgsConstructor
public enum RemittanceStatus {

    CREATED(1),

    COMPLIANCE_REJECTED(2),

    QUOTE_LOCKED(3),

    FUNDS_DEBITED(4),

    SETTLING(5),

    SETTLED(6),

    FAILED(7),

    REFUNDED(8),

    PENDING_REVIEW(9);

    private final int code;

    public static RemittanceStatus of(int code) {
        for (RemittanceStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown remittance status " + code);
    }

    /**
     * 终态不允许再变更，这是幂等消费的前提条件。
     */
    public boolean isFinal() {
        return this == SETTLED || this == REFUNDED || this == COMPLIANCE_REJECTED || this == FAILED;
    }

    /**
     * 待人工审核。它不是终态，人工放行后可继续推进，驳回则走退款。
     */
    public boolean isPendingReview() {
        return this == PENDING_REVIEW;
    }

}
