package com.dong.lab.common.result;

import com.dong.lab.common.constant.Constants;

import java.io.Serializable;

public class Result<T> implements Serializable {

    private int code;

    private String message;

    private T data;

    private long timestamp;

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(Constants.CODE_SUCCESS);
        result.setMessage(Constants.MESSAGE_SUCCESS);
        result.setData(data);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    public static <T> Result<T> fail(int code, String message, String detail) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message + " -> " + detail);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

}
