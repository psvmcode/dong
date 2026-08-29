package com.dong.lab.redpacket.dto;

public class GrabResultResponse {

    private boolean grabbed;

    private Long amount;

    private String message;

    public static GrabResultResponse success(Long amount) {
        GrabResultResponse response = new GrabResultResponse();
        response.setGrabbed(true);
        response.setAmount(amount);
        response.setMessage("grabbed");
        return response;
    }

    public static GrabResultResponse failed(String message) {
        GrabResultResponse response = new GrabResultResponse();
        response.setGrabbed(false);
        response.setAmount(0L);
        response.setMessage(message);
        return response;
    }

    public boolean isGrabbed() {
        return grabbed;
    }

    public void setGrabbed(boolean grabbed) {
        this.grabbed = grabbed;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

}
