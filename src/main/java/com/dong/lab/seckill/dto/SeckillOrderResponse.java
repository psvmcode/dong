package com.dong.lab.seckill.dto;

import com.dong.lab.seckill.entity.SeckillOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀订单响应。
 */
public class SeckillOrderResponse {

    /**
     * 订单号。
     */
    private String orderNo;

    /**
     * 秒杀活动 id。
     */
    private Long activityId;

    /**
     * 商品 id。
     */
    private Long productId;

    /**
     * 购买用户 id。
     */
    private Long userId;

    /**
     * 购买数量。
     */
    private Integer quantity;

    /**
     * 订单金额。
     */
    private BigDecimal amount;

    /**
     * 状态。
     */
    private String status;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 从实体转换为 DTO。
     */
    public static SeckillOrderResponse from(SeckillOrder order) {
        SeckillOrderResponse response = new SeckillOrderResponse();
        response.setOrderNo(order.getOrderNo());
        response.setActivityId(order.getActivityId());
        response.setProductId(order.getProductId());
        response.setUserId(order.getUserId());
        response.setQuantity(order.getQuantity());
        response.setAmount(order.getAmount());
        response.setStatus(order.getStatus() == null ? null : order.getStatus().name());
        response.setCreateTime(order.getCreateTime());
        return response;
    }

    /**
     * 获取订单号。
     */
    public String getOrderNo() {
        return orderNo;
    }

    /**
     * 设置订单号。
     */
    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    /**
     * 获取秒杀活动 id。
     */
    public Long getActivityId() {
        return activityId;
    }

    /**
     * 设置秒杀活动 id。
     */
    public void setActivityId(Long activityId) {
        this.activityId = activityId;
    }

    /**
     * 获取商品 id。
     */
    public Long getProductId() {
        return productId;
    }

    /**
     * 设置商品 id。
     */
    public void setProductId(Long productId) {
        this.productId = productId;
    }

    /**
     * 获取购买用户 id。
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 设置购买用户 id。
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * 获取购买数量。
     */
    public Integer getQuantity() {
        return quantity;
    }

    /**
     * 设置购买数量。
     */
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    /**
     * 获取订单金额。
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * 设置订单金额。
     */
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    /**
     * 获取状态。
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置状态。
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 获取创建时间。
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 设置创建时间。
     */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

}
