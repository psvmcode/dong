package com.dong.lab.seckill.dto;

import com.dong.lab.seckill.entity.SeckillActivity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SeckillActivityResponse {

    private Long id;

    private Long productId;

    private String title;

    private Integer totalStock;

    private Integer availableStock;

    private BigDecimal unitPrice;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String status;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getTotalStock() {
        return totalStock;
    }

    public void setTotalStock(Integer totalStock) {
        this.totalStock = totalStock;
    }

    public Integer getAvailableStock() {
        return availableStock;
    }

    public void setAvailableStock(Integer availableStock) {
        this.availableStock = availableStock;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}
