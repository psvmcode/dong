package com.dong.order.dto;

import com.dong.order.entity.OrderTransitionLog;
import com.dong.order.enums.OrderStatus;

import java.time.LocalDateTime;
/**
 * 状态流转日志响应。被拒绝的记录也在这里，toStatus 与 fromStatus 相同。
 */
public class OrderTransitionLogResponse {

    /**
     * 订单号。
     */
    private String orderNo;

    /**
     * 迁移前状态名。
     */
    private String fromStatus;

    /**
     * 迁移后状态名。
     */
    private String toStatus;

    /**
     * 触发的事件名。
     */
    private String event;

    /**
     * 是否推进成功。
     */
    private Boolean accepted;

    /**
     * 拒绝原因，成功时为空。
     */
    private String reason;

    /**
     * 操作人。
     */
    private String operator;

    /**
     * 发生时间。
     */
    private LocalDateTime createTime;

    /**
     * 从实体转换为 DTO。
     */
    public static OrderTransitionLogResponse from(OrderTransitionLog log) {
        OrderTransitionLogResponse response = new OrderTransitionLogResponse();
        response.setOrderNo(log.getOrderNo());
        response.setFromStatus(statusNameOf(log.getFromStatus()));
        response.setToStatus(statusNameOf(log.getToStatus()));
        response.setEvent(log.getEvent());
        response.setAccepted(log.getResult() != null && log.getResult() == 1);
        response.setReason(log.getReason());
        response.setOperator(log.getOperator());
        response.setCreateTime(log.getCreateTime());
        return response;
    }

    /**
     * 把状态编码转成状态名，0 与 null 都视为无。
     */
    private static String statusNameOf(Integer code) {
        if (code == null || code == 0) {
            return null;
        }
        return OrderStatus.of(code).name();
    }

    /**
     * 获取订单号。
     */
    public String getOrderNo() {
        return orderNo;
    }

    /**
     * 设置订单号。
     *
     * @param orderNo 订单号
     */
    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    /**
     * 获取迁移前状态名。
     */
    public String getFromStatus() {
        return fromStatus;
    }

    /**
     * 设置迁移前状态名。
     *
     * @param fromStatus 状态名
     */
    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    /**
     * 获取迁移后状态名。
     */
    public String getToStatus() {
        return toStatus;
    }

    /**
     * 设置迁移后状态名。
     *
     * @param toStatus 状态名
     */
    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

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
     * 获取是否推进成功。
     */
    public Boolean getAccepted() {
        return accepted;
    }

    /**
     * 设置是否推进成功。
     *
     * @param accepted 是否推进成功
     */
    public void setAccepted(Boolean accepted) {
        this.accepted = accepted;
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
     * 获取发生时间。
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 设置发生时间。
     *
     * @param createTime 发生时间
     */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

}
