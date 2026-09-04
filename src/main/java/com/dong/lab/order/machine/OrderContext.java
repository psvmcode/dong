package com.dong.lab.order.machine;

import com.dong.lab.order.enums.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
/**
 * 状态机上下文。COLA 是无状态状态机，当前状态和业务参数靠它传进去，
 * 迁移结果也靠它带出来：accepted 表示是否被接受，target 是目标状态。
 *
 * <p>accepted 这个标记是必需的。fireEvent 在迁移被拒绝时返回的是原状态，
 * 与内部迁移（催单）的返回值完全一样，光看返回值区分不了「被拦下」和「原地打转」，
 * 只能靠 action 有没有被执行过判断。
 */
@Data

public class OrderContext {

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 操作人或来源标识
     */
    private String operator;

    /**
     * 支付流水号，支付守卫的入参
     */
    private String payNo;

    /**
     * 物流单号，发货守卫的入参
     */
    private String trackingNo;

    /**
     * 订单应付金额，退款金额的上界
     */
    private BigDecimal payAmount;

    /**
     * 本次退款金额，退款守卫的入参
     */
    private BigDecimal refundAmount;

    /**
     * 发起退款前的状态，退款失败回退时的分支依据
     */
    private OrderStatus refundFrom;

    /**
     * 迁移后的目标状态，由 action 回填
     */
    private OrderStatus target;

    /**
     * 迁移是否被接受，由 action 回填
     */
    private boolean accepted;

    /**
     * 是否为催单，内部迁移专用标记
     */
    private boolean urge;

}
