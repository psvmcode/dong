package com.dong.tcc.enums;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;
/**
 * TCC 订单状态。反映订单在分布式事务中的整体状态。
 */
@Getter
@AllArgsConstructor

public enum TccOrderStatus {

    /**
     * 待确认，Try 阶段已完成，等待全局决策。
     */
    PENDING(1),

    /**
     * 已确认，全局事务提交，各分支正式生效。
     */
    CONFIRMED(2),

    /**
     * 已取消，全局事务回滚，各分支资源已释放。
     */
    CANCELLED(3);

    /**
     * 订单状态编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取 TCC 订单状态枚举。
     *
     * @param code 状态编码
     * @return TCC 订单状态枚举
     */
    public static TccOrderStatus of(int code) {
        for (TccOrderStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown tcc order status " + code);
    }

}
