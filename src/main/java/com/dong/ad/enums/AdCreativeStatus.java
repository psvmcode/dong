package com.dong.ad.enums;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 创意审核状态。只有已通过的创意能参与召回，计划上线时也至少要有一条。
 */
@Getter
@AllArgsConstructor
public enum AdCreativeStatus {

    /**
     * 待审核。
     */
    PENDING(0),

    /**
     * 审核通过。
     */
    APPROVED(1),

    /**
     * 审核驳回。
     */
    REJECTED(2);

    /**
     * 状态编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取审核状态枚举。
     *
     * @param code 状态编码
     * @return 审核状态枚举
     */
    public static AdCreativeStatus of(int code) {
        for (AdCreativeStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown ad creative status " + code);
    }

}
