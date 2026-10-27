package com.dong.rpc.protocol;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * RPC 响应状态。
 *
 * <p>状态要在线路上传输，所以编码成 int 而不是枚举名——
 * 枚举名一旦改名，新老节点之间就对不上了；数值则是稳定的契约。
 *
 * <p>这里刻意把「业务异常」也归到 SERVER_ERROR：RPC 层不传递调用方的异常类型，
 * 否则服务端的类一旦改名，客户端反序列化时就会 ClassNotFound。
 * 真正需要区分错误语义的场景，应该在响应体里带业务错误码，而不是靠异常类名。
 */
@Getter
@RequiredArgsConstructor
public enum RpcStatus {

    /**
     * 调用成功。
     */
    OK(0, "ok"),

    /**
     * 服务不存在：注册中心里没有这个服务，或者实例全部过期。
     */
    SERVICE_NOT_FOUND(1, "service not found"),

    /**
     * 方法不存在：服务在，但方法名或参数类型对不上。
     */
    METHOD_NOT_FOUND(2, "method not found"),

    /**
     * 编解码失败：请求或响应的字节不符合约定。
     */
    CODEC_ERROR(3, "codec error"),

    /**
     * 超时：客户端在超时时间内没等到响应。
     */
    TIMEOUT(4, "timeout"),

    /**
     * 服务端错误：服务抛了异常。
     */
    SERVER_ERROR(5, "server error");

    private final int code;

    private final String text;

    /**
     * 按数值反查。
     *
     * @param code 状态值
     * @return 状态，未知值按服务端错误返回
     */
    public static RpcStatus of(int code) {
        for (RpcStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return SERVER_ERROR;
    }

}
