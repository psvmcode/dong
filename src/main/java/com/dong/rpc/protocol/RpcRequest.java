package com.dong.rpc.protocol;

import com.dong.rpc.annotation.ProtoField;
import com.dong.rpc.annotation.ProtoMessage;
import com.dong.rpc.codec.ProtoType;
import lombok.Data;

import java.util.List;

/**
 * RPC 请求。
 *
 * <p>参数是「类型名 + 已编码的字节」两个列表，而不是直接放对象：
 * 服务端可能根本没有客户端那个参数类，直接传对象会要求两端类完全一致；
 * 传字节则把「怎么解释这段字节」的决定权交给服务端自己的类路径，
 * 这也是泛化调用（不依赖接口 jar 也能调）能成立的基础。
 *
 * <p>requestId 是客户端挂起等待与响应对上的唯一凭据：
 * 单条连接上并发跑着很多请求，响应是乱序回来的，没有它就只能串行调用。
 */
@Data
@ProtoMessage
public class RpcRequest {

    /**
     * 请求 id，客户端生成，用于把响应对回挂起的调用。
     */
    @ProtoField(order = 1, type = ProtoType.INT64)
    private long requestId;

    /**
     * 服务名，默认是接口全限定名。
     */
    @ProtoField(order = 2, type = ProtoType.STRING)
    private String serviceName;

    /**
     * 方法名。
     */
    @ProtoField(order = 3, type = ProtoType.STRING)
    private String methodName;

    /**
     * 参数类型全限定名，与 arguments 一一对应。
     */
    @ProtoField(order = 4, type = ProtoType.STRING, repeated = true)
    private List<String> parameterTypes;

    /**
     * 已编码的参数字节，与 parameterTypes 一一对应。
     */
    @ProtoField(order = 5, type = ProtoType.BYTES, repeated = true)
    private List<byte[]> arguments;

    /**
     * 客户端发起时间，毫秒时间戳。服务端用它算「在网络上漂了多久」。
     */
    @ProtoField(order = 6, type = ProtoType.INT64)
    private long sentAt;

}
