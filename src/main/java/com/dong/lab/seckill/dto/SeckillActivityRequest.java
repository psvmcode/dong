package com.dong.lab.seckill.dto;

import com.dong.lab.seckill.entity.SeckillActivity;
import com.dong.lab.seckill.enums.SeckillActivityStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SeckillActivityRequest {

    @NotNull
    private Long productId;

    @NotBlank
    private String title;

    @NotNull
    @Min(1)
    private Integer totalStock;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal unitPrice;

    @NotNull
    private LocalDateTime startTime;

    @NotNull
    private LocalDateTime endTime;

    public SeckillActivity toEntity() {
        SeckillActivity activity = new SeckillActivity();
        activity.setProductId(productId);
        activity.setTitle(title);
        activity.setTotalStock(totalStock);
        activity.setAvailableStock(totalStock);
        activity.setUnitPrice(unitPrice);
        activity.setStartTime(startTime);
        activity.setEndTime(endTime);
        activity.setStatus(SeckillActivityStatus.DRAFT);
        activity.setVersion(0);
        return activity;
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

}
