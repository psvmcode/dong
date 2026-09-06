package com.dong.common.result;

import com.dong.common.constant.Constants;

import java.io.Serializable;

/**
 * 统一响应结果。
 *
 * @param <T> 数据类型
 */
public class Result<T> implements Serializable {

    /**
     * 响应编码。
     */
    private int code;

    /**
     * 提示消息。
     */
    private String message;

    /**
     * 响应数据。
     */
    private T data;

    /**
     * 响应时间戳。
     */
    private long timestamp;

    /**
     * 创建成功响应（无数据）。
     *
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 创建成功响应。
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return 成功响应
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
     * 创建失败响应。
     *
     * @param code    错误编码
     * @param message 提示消息
     * @param <T>     数据类型
     * @return 失败响应
     */
    public static <T> Result<T> fail(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    /**
     * 创建带详情的失败响应。
     *
     * @param code    错误编码
     * @param message 提示消息
     * @param detail  错误详情
     * @param <T>     数据类型
     * @return 失败响应
     */
    public static <T> Result<T> fail(int code, String message, String detail) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message + " -> " + detail);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    /**
     * 获取响应编码。
     *
     * @return 响应编码
     */
    public int getCode() {
        return code;
    }

    /**
     * 设置响应编码。
     *
     * @param code 响应编码
     */
    public void setCode(int code) {
        this.code = code;
    }

    /**
     * 获取提示消息。
     *
     * @return 提示消息
     */
    public String getMessage() {
        return message;
    }

    /**
     * 设置提示消息。
     *
     * @param message 提示消息
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 获取响应数据。
     *
     * @return 响应数据
     */
    public T getData() {
        return data;
    }

    /**
     * 设置响应数据。
     *
     * @param data 响应数据
     */
    public void setData(T data) {
        this.data = data;
    }

    /**
     * 获取响应时间戳。
     *
     * @return 响应时间戳
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * 设置响应时间戳。
     *
     * @param timestamp 响应时间戳
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

}
