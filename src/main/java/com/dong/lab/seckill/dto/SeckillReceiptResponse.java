package com.dong.lab.seckill.dto;

import java.math.BigDecimal;

/**
 * 秒杀受理凭证响应。
 */
public class SeckillReceiptResponse {

    /**
     * 订单号。
     */
    private String orderNo;

    /**
     * 是否受理成功。
     */
    private boolean accepted;

    /**
     * 结果提示信息。
     */
    private String message;

    /**
     * 扣减后的剩余库存。
     */
    private int remainingStock;

    /**
     * 订单金额。
     */
    private BigDecimal amount;

    /**
     * 构造受理成功的秒杀响应。
     */
    public static SeckillReceiptResponse accepted(String orderNo, int remainingStock, BigDecimal amount) {
        SeckillReceiptResponse response = new SeckillReceiptResponse();
        response.setOrderNo(orderNo);
        response.setAccepted(true);
        response.setMessage("queued");
        response.setRemainingStock(remainingStock);
        response.setAmount(amount);
        return response;
    }

    /**
     * 构造被拒绝的秒杀响应。
     */
    public static SeckillReceiptResponse rejected(String message) {
        SeckillReceiptResponse response = new SeckillReceiptResponse();
        response.setAccepted(false);
        response.setMessage(message);
        response.setAmount(BigDecimal.ZERO);
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
     * 获取是否受理成功。
     */
    public boolean isAccepted() {
        return accepted;
    }

    /**
     * 设置是否受理成功。
     */
    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    /**
     * 获取结果提示信息。
     */
    public String getMessage() {
        return message;
    }

    /**
     * 设置结果提示信息。
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 获取扣减后的剩余库存。
     */
    public int getRemainingStock() {
        return remainingStock;
    }

    /**
     * 设置扣减后的剩余库存。
     */
    public void setRemainingStock(int remainingStock) {
        this.remainingStock = remainingStock;
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

}
