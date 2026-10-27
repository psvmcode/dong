package com.dong.rpc.dto;

import lombok.Data;

/**
 * RPC 运行状况。客户端与服务端的计数分开列：
 * 两边数字对不上就说明有请求在路上丢了，或者根本没被发出去。
 */
@Data
public class RpcStatsResponse {

    /**
     * 服务端是否在监听。
     */
    private boolean serverRunning;

    /**
     * 服务端端口。
     */
    private int serverPort;

    /**
     * 服务端收到的调用次数。
     */
    private long serverInvocations;

    /**
     * 服务端失败的调用次数。
     */
    private long serverFailures;

    /**
     * 客户端发出的调用次数。
     */
    private long clientRequests;

    /**
     * 客户端失败的调用次数。
     */
    private long clientFailures;

    /**
     * 客户端成功调用的平均耗时，毫秒。
     */
    private long clientAverageElapsedMs;

    /**
     * 服务列表本地缓存的命中次数。
     */
    private long discoveryCacheHits;

    /**
     * 服务列表本地缓存的未命中次数，即真正访问了注册中心的次数。
     */
    private long discoveryCacheMisses;

}
