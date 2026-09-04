package com.dong.lab.order.dto;

import com.dong.lab.order.entity.TradeOrder;
import com.dong.lab.order.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 订单响应。状态同时给枚举名和编码，前者方便看，后者方便对库。
 */
public class OrderResponse {

    /**
     * 订单号。
     */
    private String orderNo;

    /**
     * 下单用户 id。
     */
    private Long userId;

    /**
     * 商品名称。
     */
    private String productName;

    /**
     * 购买数量。
     */
    private Integer quantity;

    /**
     * 应付金额。
     */
    private BigDecimal payAmount;

    /**
     * 已退金额。
     */
    private BigDecimal refundAmount;

    /**
     * 状态名。
     */
    private String status;

    /**
     * 状态编码。
     */
    private Integer statusCode;

    /**
     * 发起退款前的状态名，未曾退款时为空。
     */
    private String refundFrom;

    /**
     * 物流单号。
     */
    private String trackingNo;

    /**
     * 支付流水号。
     */
    private String payNo;

    /**
     * 催单次数。
     */
    private Integer urgeCount;

    /**
     * 乐观锁版本号。
     */
    private Integer version;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;

    /**
     * 从实体转换为 DTO。
     */
    public static OrderResponse from(TradeOrder order) {
        OrderResponse response = new OrderResponse();
        response.setOrderNo(order.getOrderNo());
        response.setUserId(order.getUserId());
        response.setProductName(order.getProductName());
        response.setQuantity(order.getQuantity());
        response.setPayAmount(order.getPayAmount());
        response.setRefundAmount(order.getRefundAmount());
        response.setStatus(order.getStatus() == null ? null : order.getStatus().name());
        response.setStatusCode(order.getStatus() == null ? null : order.getStatus().getCode());
        response.setRefundFrom(statusNameOf(order.getRefundFrom()));
        response.setTrackingNo(order.getTrackingNo());
        response.setPayNo(order.getPayNo());
        response.setUrgeCount(order.getUrgeCount());
        response.setVersion(order.getVersion());
        response.setCreateTime(order.getCreateTime());
        response.setUpdateTime(order.getUpdateTime());
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
     * 获取下单用户 id。
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 设置下单用户 id。
     *
     * @param userId 下单用户 id
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * 获取商品名称。
     */
    public String getProductName() {
        return productName;
    }

    /**
     * 设置商品名称。
     *
     * @param productName 商品名称
     */
    public void setProductName(String productName) {
        this.productName = productName;
    }

    /**
     * 获取购买数量。
     */
    public Integer getQuantity() {
        return quantity;
    }

    /**
     * 设置购买数量。
     *
     * @param quantity 购买数量
     */
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    /**
     * 获取应付金额。
     */
    public BigDecimal getPayAmount() {
        return payAmount;
    }

    /**
     * 设置应付金额。
     *
     * @param payAmount 应付金额
     */
    public void setPayAmount(BigDecimal payAmount) {
        this.payAmount = payAmount;
    }

    /**
     * 获取已退金额。
     */
    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    /**
     * 设置已退金额。
     *
     * @param refundAmount 已退金额
     */
    public void setRefundAmount(BigDecimal refundAmount) {
        this.refundAmount = refundAmount;
    }

    /**
     * 获取状态名。
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置状态名。
     *
     * @param status 状态名
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 获取状态编码。
     */
    public Integer getStatusCode() {
        return statusCode;
    }

    /**
     * 设置状态编码。
     *
     * @param statusCode 状态编码
     */
    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    /**
     * 获取发起退款前的状态名。
     */
    public String getRefundFrom() {
        return refundFrom;
    }

    /**
     * 设置发起退款前的状态名。
     *
     * @param refundFrom 状态名
     */
    public void setRefundFrom(String refundFrom) {
        this.refundFrom = refundFrom;
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
     * 获取催单次数。
     */
    public Integer getUrgeCount() {
        return urgeCount;
    }

    /**
     * 设置催单次数。
     *
     * @param urgeCount 催单次数
     */
    public void setUrgeCount(Integer urgeCount) {
        this.urgeCount = urgeCount;
    }

    /**
     * 获取乐观锁版本号。
     */
    public Integer getVersion() {
        return version;
    }

    /**
     * 设置乐观锁版本号。
     *
     * @param version 版本号
     */
    public void setVersion(Integer version) {
        this.version = version;
    }

    /**
     * 获取创建时间。
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 设置创建时间。
     *
     * @param createTime 创建时间
     */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    /**
     * 获取更新时间。
     */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    /**
     * 设置更新时间。
     *
     * @param updateTime 更新时间
     */
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

}
