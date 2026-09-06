package com.dong.seckill.dto;

import com.dong.seckill.entity.SeckillActivity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动响应。
 */
public class SeckillActivityResponse {

    /**
     * 唯一标识 id。
     */
    private Long id;

    /**
     * 参与秒杀的商品 id。
     */
    private Long productId;

    /**
     * 活动标题。
     */
    private String title;

    /**
     * 活动总库存。
     */
    private Integer totalStock;

    /**
     * 剩余库存。
     */
    private Integer availableStock;

    /**
     * 秒杀单价。
     */
    private BigDecimal unitPrice;

    /**
     * 活动开始时间。
     */
    private LocalDateTime startTime;

    /**
     * 活动结束时间。
     */
    private LocalDateTime endTime;

    /**
     * 状态。
     */
    private String status;

    /**
     * 从实体转换为 DTO。
     */
    public static SeckillActivityResponse from(SeckillActivity activity) {
        SeckillActivityResponse response = new SeckillActivityResponse();
        response.setId(activity.getId());
        response.setProductId(activity.getProductId());
        response.setTitle(activity.getTitle());
        response.setTotalStock(activity.getTotalStock());
        response.setAvailableStock(activity.getAvailableStock());
        response.setUnitPrice(activity.getUnitPrice());
        response.setStartTime(activity.getStartTime());
        response.setEndTime(activity.getEndTime());
        response.setStatus(activity.getStatus() == null ? null : activity.getStatus().name());
        return response;
    }

    /**
     * 获取唯一标识 id。
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置唯一标识 id。
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取参与秒杀的商品 id。
     */
    public Long getProductId() {
        return productId;
    }

    /**
     * 设置参与秒杀的商品 id。
     */
    public void setProductId(Long productId) {
        this.productId = productId;
    }

    /**
     * 获取活动标题。
     */
    public String getTitle() {
        return title;
    }

    /**
     * 设置活动标题。
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * 获取活动总库存。
     */
    public Integer getTotalStock() {
        return totalStock;
    }

    /**
     * 设置活动总库存。
     */
    public void setTotalStock(Integer totalStock) {
        this.totalStock = totalStock;
    }

    /**
     * 获取剩余库存。
     */
    public Integer getAvailableStock() {
        return availableStock;
    }

    /**
     * 设置剩余库存。
     */
    public void setAvailableStock(Integer availableStock) {
        this.availableStock = availableStock;
    }

    /**
     * 获取秒杀单价。
     */
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    /**
     * 设置秒杀单价。
     */
    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    /**
     * 获取活动开始时间。
     */
    public LocalDateTime getStartTime() {
        return startTime;
    }

    /**
     * 设置活动开始时间。
     */
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    /**
     * 获取活动结束时间。
     */
    public LocalDateTime getEndTime() {
        return endTime;
    }

    /**
     * 设置活动结束时间。
     */
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
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

}
