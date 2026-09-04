package com.dong.lab.tcc.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * TCC 分布式订单请求参数。
 */
public class TccOrderRequest {

    @NotNull
    /**
     * 下单用户 id。
     */
    private Long userId;

    @NotNull
    /**
     * 商品 id。
     */
    private Long productId;

    @NotNull
    @Min(1)
    /**
     * 购买数量。
     */
    private Integer quantity;

    /**
     * 是否强制触发失败，用于验证回滚。
     */
    private boolean forceFailure;

    /**
     * 获取下单用户 id。
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 设置下单用户 id。
     */
    public void setUserId(Long userId) {
        this.userId = userId;
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
     * 获取是否强制触发失败。
     */
    public boolean isForceFailure() {
        return forceFailure;
    }

    /**
     * 设置是否强制触发失败。
     */
    public void setForceFailure(boolean forceFailure) {
        this.forceFailure = forceFailure;
    }

}
