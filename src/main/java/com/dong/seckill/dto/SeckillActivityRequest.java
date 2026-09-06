package com.dong.seckill.dto;

import com.dong.seckill.entity.SeckillActivity;
import com.dong.seckill.enums.SeckillActivityStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动请求参数。
 */
public class SeckillActivityRequest {

    /**
     * 参与秒杀的商品 id。
     */
    @NotNull
    private Long productId;

    /**
     * 活动标题。
     */
    @NotBlank
    private String title;

    /**
     * 活动总库存。
     */
    @NotNull
    @Min(1)
    private Integer totalStock;

    /**
     * 秒杀单价。
     */
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal unitPrice;

    /**
     * 活动开始时间。
     */
    @NotNull
    private LocalDateTime startTime;

    /**
     * 活动结束时间。
     */
    @NotNull
    private LocalDateTime endTime;

    /**
     * 转换为秒杀活动实体。
     */
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

}
