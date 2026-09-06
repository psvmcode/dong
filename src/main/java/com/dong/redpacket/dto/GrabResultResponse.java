package com.dong.redpacket.dto;

/**
 * 抢红包结果响应。
 */
public class GrabResultResponse {

    /**
     * 是否抢到。
     */
    private boolean grabbed;

    /**
     * 抢到金额，单位分。
     */
    private Long amount;

    /**
     * 结果提示信息。
     */
    private String message;

    /**
     * 构造抢到红包的成功响应。
     */
    public static GrabResultResponse success(Long amount) {
        GrabResultResponse response = new GrabResultResponse();
        response.setGrabbed(true);
        response.setAmount(amount);
        response.setMessage("grabbed");
        return response;
    }

    /**
     * 构造抢红包失败的响应。
     */
    public static GrabResultResponse failed(String message) {
        GrabResultResponse response = new GrabResultResponse();
        response.setGrabbed(false);
        response.setAmount(0L);
        response.setMessage(message);
        return response;
    }

    /**
     * 获取是否抢到。
     */
    public boolean isGrabbed() {
        return grabbed;
    }

    /**
     * 设置是否抢到。
     */
    public void setGrabbed(boolean grabbed) {
        this.grabbed = grabbed;
    }

    /**
     * 获取抢到金额。
     */
    public Long getAmount() {
        return amount;
    }

    /**
     * 设置抢到金额。
     */
    public void setAmount(Long amount) {
        this.amount = amount;
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

}
