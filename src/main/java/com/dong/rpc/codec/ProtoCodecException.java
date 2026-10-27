package com.dong.rpc.codec;

/**
 * 编解码失败。
 *
 * <p>单独定义而不是复用 IllegalArgumentException：序列化错误是「数据格式坏了」，
 * 属于服务端内部错误，不应该和「调用方传参错了」混成同一个错误码。
 */
public class ProtoCodecException extends RuntimeException {

    /**
     * 用消息构造。
     *
     * @param message 错误信息
     */
    public ProtoCodecException(String message) {
        super(message);
    }

    /**
     * 用消息与原因构造。
     *
     * @param message 错误信息
     * @param cause   原因
     */
    public ProtoCodecException(String message, Throwable cause) {
        super(message, cause);
    }

}
