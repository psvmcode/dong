package com.dong.rpc.protocol;

import com.dong.rpc.annotation.ProtoField;
import com.dong.rpc.annotation.ProtoMessage;
import com.dong.rpc.codec.ProtoType;
import lombok.Data;

/**
 * RPC 响应。
 *
 * <p>返回值同样是编码后的字节：客户端按自己手里接口方法的返回类型解码，
 * 服务端不需要知道客户端用的是哪个版本的返回类。
 *
 * <p>serverElapsed 单独传而不是让客户端自己算：
 * 客户端算出来的「耗时」里混着网络往返、排队、编解码，
 * 只有服务端自己的执行时间才能回答「这次调用是不是慢在服务本身」。
 */
@Data
@ProtoMessage
public class RpcResponse {

    /**
     * 对应的请求 id。
     */
    @ProtoField(order = 1, type = ProtoType.INT64)
    private long requestId;

    /**
     * 状态值，见 RpcStatus。
     */
    @ProtoField(order = 2, type = ProtoType.INT32)
    private int status;

    /**
     * 错误描述，成功时为空。
     */
    @ProtoField(order = 3, type = ProtoType.STRING)
    private String message;

    /**
     * 已编码的返回值，void 或 null 时为空。
     */
    @ProtoField(order = 4, type = ProtoType.BYTES)
    private byte[] payload;

    /**
     * 服务端执行耗时，毫秒。
     */
    @ProtoField(order = 5, type = ProtoType.INT64)
    private long serverElapsed;

    /**
     * 构造成功响应。
     *
     * @param requestId     请求 id
     * @param payload       返回值字节
     * @param serverElapsed 服务端耗时
     * @return 响应
     */
    public static RpcResponse ok(long requestId, byte[] payload, long serverElapsed) {
        RpcResponse response = new RpcResponse();
        response.setRequestId(requestId);
        response.setStatus(RpcStatus.OK.getCode());
        response.setMessage("");
        response.setPayload(payload);
        response.setServerElapsed(serverElapsed);
        return response;
    }

    /**
     * 构造失败响应。
     *
     * @param requestId 请求 id
     * @param status    状态
     * @param message   描述
     * @return 响应
     */
    public static RpcResponse fail(long requestId, RpcStatus status, String message) {
        RpcResponse response = new RpcResponse();
        response.setRequestId(requestId);
        response.setStatus(status.getCode());
        response.setMessage(message == null ? "" : message);
        response.setPayload(null);
        return response;
    }

    /**
     * 状态枚举。
     *
     * @return 状态
     */
    public RpcStatus statusEnum() {
        return RpcStatus.of(status);
    }

}
