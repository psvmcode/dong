package com.dong.lab.seckill.dto;

import com.dong.lab.seckill.entity.SeckillOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SeckillOrderResponse {

    private String orderNo;

    private Long activityId;

    private Long productId;

    private Long userId;

    private Integer quantity;

    private BigDecimal amount;

    private String status;

    private LocalDateTime createTime;

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

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getActivityId() {
        return activityId;
    }

    public void setActivityId(Long activityId) {
        this.activityId = activityId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

}
