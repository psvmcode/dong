package com.dong.lab.order.dto;

import com.dong.lab.order.entity.TradeOrder;
import com.dong.lab.order.enums.OrderStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
/**
 * 创建订单请求。订单一经创建就停在待支付，后续只能靠事件推进。
 */
public class OrderCreateRequest {

    /**
     * 下单用户 id。
     */
    @NotNull
    private Long userId;

    /**
     * 商品名称。
     */
    @NotBlank
    private String productName;

    /**
     * 购买数量。
     */
    @Min(1)
    private Integer quantity = 1;

    /**
     * 应付金额。
     */
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal payAmount;

    /**
     * 转换为订单实体。订单号由服务层生成，这里只填业务字段。
     */
    public TradeOrder toEntity() {
        TradeOrder order = new TradeOrder();
        order.setUserId(userId);
        order.setProductName(productName);
        order.setQuantity(quantity);
        order.setPayAmount(payAmount);
        order.setRefundAmount(BigDecimal.ZERO);
        order.setStatus(OrderStatus.WAIT_PAY);
        order.setRefundFrom(0);
        order.setTrackingNo("");
        order.setPayNo("");
        order.setUrgeCount(0);
        order.setVersion(0);
        return order;
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

}
