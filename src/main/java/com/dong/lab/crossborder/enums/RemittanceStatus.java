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

    /**
     * 已创建，汇款单已生成，等待后续合规与报价处理。
     */
    CREATED(1),

    /**
     * 合规拒绝，无法继续推进，后续走退款流程。
     */
    COMPLIANCE_REJECTED(2),

    /**
     * 已锁价，报价已锁定，等待资金扣减。
     */
    QUOTE_LOCKED(3),

    /**
     * 已扣款，付款账户资金已扣除，等待清算。
     */
    FUNDS_DEBITED(4),

    /**
     * 清算中，汇款已进入清算渠道处理。
     */
    SETTLING(5),

    /**
     * 已完成清算，资金已到账。
     */
    SETTLED(6),

    /**
     * 失败，清算或扣款失败，后续走退款流程。
     */
    FAILED(7),

    /**
     * 已退款，失败或被拒绝后资金已退回原账户。
     */
    REFUNDED(8),

    /**
     * 待人工审核，合规人员放行后继续推进，驳回则走退款。
     */
    PENDING_REVIEW(9);

    /**
     * 汇款状态编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取汇款单状态枚举。
     *
     * @param code 状态编码
     * @return 汇款单状态枚举
     */
    public static RemittanceStatus of(int code) {
        for (RemittanceStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown remittance status " + code);
    }

    /**
     * 判断是否为终态。终态不允许再变更，是幂等消费的前提条件。
     *
     * @return true 表示已到达终态
     */
    public boolean isFinal() {
        return this == SETTLED || this == REFUNDED || this == COMPLIANCE_REJECTED || this == FAILED;
    }

    /**
     * 判断是否为待人工审核状态。
     *
     * @return true 表示待人工审核
     */
    public boolean isPendingReview() {
        return this == PENDING_REVIEW;
    }

}
