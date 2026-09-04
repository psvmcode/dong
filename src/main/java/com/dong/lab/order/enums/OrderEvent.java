package com.dong.lab.order.enums;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;
/**
 * 订单事件。事件是推进状态的唯一入口，业务代码不允许直接改状态字段。
 */
@Getter
@AllArgsConstructor

public enum OrderEvent {

    /**
     * 支付，买家完成付款。
     */
    PAY(1),

    /**
     * 取消，买家主动取消订单。
     */
    CANCEL(2),

    /**
     * 超时，超过支付时限仍未付款，由定时任务触发。
     */
    TIMEOUT(3),

    /**
     * 发货，商家填写物流单号并发出货物。
     */
    SHIP(4),

    /**
     * 确认收货，买家签收。
     */
    RECEIVE(5),

    /**
     * 催单，买家催促发货，状态不变只累加次数。
     */
    URGE(6),

    /**
     * 申请退款，买家发起退款。
     */
    APPLY_REFUND(7),

    /**
     * 退款成功，资金已退回买家。
     */
    REFUND_SUCCESS(8),

    /**
     * 退款失败，退回发起退款前的状态。
     */
    REFUND_FAIL(9);

    /**
     * 事件编码。
     */
    private final int code;

    /**
     * 根据编码获取订单事件枚举。
     *
     * @param code 事件编码
     * @return 订单事件枚举
     */
    public static OrderEvent of(int code) {
        for (OrderEvent event : values()) {
            if (event.getCode() == code) {
                return event;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown order event " + code);
    }

    /**
     * 按事件名获取枚举，接口层传的是字符串。
     *
     * @param name 事件名
     * @return 订单事件枚举
     */
    public static OrderEvent ofName(String name) {
        try {
            return OrderEvent.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown order event " + name);
        }
    }

}
