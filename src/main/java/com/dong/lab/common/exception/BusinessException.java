package com.dong.lab.common.exception;

/**
 * BusinessException。
 */
public class BusinessException extends RuntimeException {

    /**
     * 编码。
     */
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * getCode。
     */
    public int getCode() {
        return code;
    }

}
