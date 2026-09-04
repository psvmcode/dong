package com.dong.lab.common.exception;

/**
 * 业务异常。
 */
public class BusinessException extends RuntimeException {

    /**
     * 错误编码。
     */
    private final int code;

    /**
     * 构造业务异常。
     *
     * @param code    错误编码
     * @param message 提示消息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造带原因的业务异常。
     *
     * @param code    错误编码
     * @param message 提示消息
     * @param cause   原始异常
     */
    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * 获取错误编码。
     *
     * @return 错误编码
     */
    public int getCode() {
        return code;
    }

}
