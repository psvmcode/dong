package com.dong.crossborder.enums;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;
/**
 * 记账方向。DEBIT 表示账户减少，CREDIT 表示账户增加。
 * 借贷分离是为了让流水可复式核对：同一笔汇款在付款方和收款方各有一条流水，
 * 方向相反，金额按各自币种记录。
 */
@Getter
@AllArgsConstructor

public enum LedgerDirection {

    /**
     * 借方，账户余额减少。
     */
    DEBIT(1),

    /**
     * 贷方，账户余额增加。
     */
    CREDIT(2);

    /**
     * 记账方向编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取记账方向枚举。
     *
     * @param code 方向编码
     * @return 记账方向枚举
     */
    public static LedgerDirection of(int code) {
        for (LedgerDirection direction : values()) {
            if (direction.getCode() == code) {
                return direction;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown ledger direction " + code);
    }

}
