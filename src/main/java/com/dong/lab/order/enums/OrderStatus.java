package com.dong.lab.order.enums;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;
/**
 * 订单履约状态。七个状态覆盖正常履约、取消、退款三条链路，
 * 状态机的职责就是保证订单只能沿着预设链路推进，跳步和倒退都在框架层被拦掉。
 */
@Getter
@AllArgsConstructor

public enum OrderStatus {

    /**
     * 待支付，订单已创建，等待买家付款。
     */
    WAIT_PAY(1),

    /**
     * 待发货，买家已完成支付，等待商家发货。
     */
    WAIT_SHIP(2),

    /**
     * 待收货，商家已发货，等待买家确认收货。
     */
    WAIT_RECEIVE(3),

    /**
     * 已完成，买家已确认收货，订单正常结束。
     */
    FINISHED(4),

    /**
     * 已取消，买家主动取消或超时未支付自动关单。
     */
    CANCELLED(5),

    /**
     * 退款中，买家已发起退款，等待退款结果。
     */
    REFUNDING(6),

    /**
     * 已退款，退款已到账，订单终结。
     */
    REFUNDED(7);

    /**
     * 状态编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取订单状态枚举。
     *
     * @param code 状态编码
     * @return 订单状态枚举
     */
    public static OrderStatus of(int code) {
        for (OrderStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown order status " + code);
    }

    /**
     * 判断是否为终态。终态不接受任何事件，这是幂等处理的前提。
     *
     * @return true 表示订单已终结
     */
    public boolean isFinal() {
        return this == FINISHED || this == CANCELLED || this == REFUNDED;
    }

    /**
     * 判断是否处于可发起退款的阶段。已完成要走售后流程，已取消没有资金往来，
     * 两者都不在退款状态机内。
     *
     * @return true 表示可发起退款
     */
    public boolean isRefundable() {
        return this == WAIT_SHIP || this == WAIT_RECEIVE;
    }

}
