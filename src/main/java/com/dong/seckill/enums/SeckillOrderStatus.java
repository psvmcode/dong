package com.dong.seckill.enums;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;
/**
 * 秒杀订单状态。描述订单从创建到支付或取消的流转过程。
 */
@Getter
@AllArgsConstructor

public enum SeckillOrderStatus {

    /**
     * 待支付，订单已生成但尚未完成付款。
     */
    PENDING_PAYMENT(0),

    /**
     * 已支付，用户完成付款，等待发货或履约。
     */
    PAID(1),

    /**
     * 已取消，超时未支付或用户主动取消。
     */
    CANCELLED(2);

    /**
     * 状态编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取秒杀订单状态枚举。
     *
     * @param code 状态编码
     * @return 秒杀订单状态枚举
     */
    public static SeckillOrderStatus of(int code) {
        for (SeckillOrderStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown seckill order status " + code);
    }

}
