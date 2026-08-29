package com.dong.lab.seckill.dto;

import java.math.BigDecimal;

public class SeckillReceiptResponse {

    private String orderNo;

    private boolean accepted;

    private String message;

    private int remainingStock;

    private BigDecimal amount;

    public static SeckillReceiptResponse accepted(String orderNo, int remainingStock, BigDecimal amount) {
        SeckillReceiptResponse response = new SeckillReceiptResponse();
        response.setOrderNo(orderNo);
        response.setAccepted(true);
        response.setMessage("queued");
        response.setRemainingStock(remainingStock);
        response.setAmount(amount);
        return response;
    }

    public static SeckillReceiptResponse rejected(String message) {
        SeckillReceiptResponse response = new SeckillReceiptResponse();
        response.setAccepted(false);
        response.setMessage(message);
        response.setAmount(BigDecimal.ZERO);
        return response;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getRemainingStock() {
        return remainingStock;
    }

    public void setRemainingStock(int remainingStock) {
        this.remainingStock = remainingStock;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

}
