package com.dong.lab.common.result;

import com.dong.lab.common.constant.Constants;

import java.io.Serializable;

/**
 * Result<T>。
 */
public class Result<T> implements Serializable {

    /**
     * 编码。
     */
    private int code;

    /**
     * message。
     */
    private String message;

    /**
     * data。
     */
    private T data;

    /**
     * timestamp。
     */
    private long timestamp;

    /**
     * success。
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * success。
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(Constants.CODE_SUCCESS);
        result.setMessage(Constants.MESSAGE_SUCCESS);
        result.setData(data);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    /**
     * fail。
     */
    public static <T> Result<T> fail(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    /**
     * fail。
     */
    public static <T> Result<T> fail(int code, String message, String detail) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message + " -> " + detail);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    /**
     * getCode。
     */
    public int getCode() {
        return code;
    }

    /**
     * setCode。
     */
    public void setCode(int code) {
        this.code = code;
    }

    /**
     * getMessage。
     */
    public String getMessage() {
        return message;
    }

    /**
     * setMessage。
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * getData。
     */
    public T getData() {
        return data;
    }

    /**
     * setData。
     */
    public void setData(T data) {
        this.data = data;
    }

    /**
     * getTimestamp。
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * setTimestamp。
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

}
