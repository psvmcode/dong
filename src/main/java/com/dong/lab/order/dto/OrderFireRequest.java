package com.dong.lab.order.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
/**
 * 触发订单事件请求。事件名传枚举字面量，其余字段按需携带，
 * 缺了对应字段会被守卫拦下而不是报错，这正是守卫存在的意义。
 */
public class OrderFireRequest {

    /**
     * 事件名，取值见 OrderEvent。
     */
    @NotBlank
    private String event;

    /**
     * 操作人或来源标识。
     */
    private String operator;

    /**
     * 支付流水号，支付事件必填。
     */
    private String payNo;

    /**
     * 物流单号，发货事件必填。
     */
    private String trackingNo;

    /**
     * 退款金额，申请退款事件必填。
     */
    private BigDecimal refundAmount;

    /**
     * 拒绝原因，取消或退款失败时填写。
     */
    private String reason;

    /**
     * 获取事件名。
     */
    public String getEvent() {
        return event;
    }

    /**
     * 设置事件名。
     *
     * @param event 事件名
     */
    public void setEvent(String event) {
        this.event = event;
    }

    /**
     * 获取操作人。
     */
    public String getOperator() {
        return operator;
    }

    /**
     * 设置操作人。
     *
     * @param operator 操作人
     */
    public void setOperator(String operator) {
        this.operator = operator;
    }

    /**
     * 获取支付流水号。
     */
    public String getPayNo() {
        return payNo;
    }

    /**
     * 设置支付流水号。
     *
     * @param payNo 支付流水号
     */
    public void setPayNo(String payNo) {
        this.payNo = payNo;
    }

    /**
     * 获取物流单号。
     */
    public String getTrackingNo() {
        return trackingNo;
    }

    /**
     * 设置物流单号。
     *
     * @param trackingNo 物流单号
     */
    public void setTrackingNo(String trackingNo) {
        this.trackingNo = trackingNo;
    }

    /**
     * 获取退款金额。
     */
    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    /**
     * 设置退款金额。
     *
     * @param refundAmount 退款金额
     */
    public void setRefundAmount(BigDecimal refundAmount) {
        this.refundAmount = refundAmount;
    }

    /**
     * 获取拒绝原因。
     */
    public String getReason() {
        return reason;
    }

    /**
     * 设置拒绝原因。
     *
     * @param reason 拒绝原因
     */
    public void setReason(String reason) {
        this.reason = reason;
    }

}
