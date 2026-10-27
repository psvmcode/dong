package com.dong.rpc.transport;

import com.dong.rpc.codec.ProtoCodec;
import com.dong.rpc.protocol.RpcResponse;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 客户端处理器：收到响应后按 requestId 唤醒挂起的调用。
 *
 * <p>单条连接上并发跑着多个请求，响应是乱序回来的，
 * 靠 requestId 才能把结果对回各自的调用者——这也是 RPC 与「一问一答的 HTTP」在
 * 连接复用上的关键差别。
 */
@Slf4j
@RequiredArgsConstructor
public class RpcClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

    /**
     * 请求 id 到挂起调用的映射，与客户端共享同一个实例。
     */
    private final Map<Long, CompletableFuture<RpcResponse>> pending;

    @Override
    protected void channelRead0(ChannelHandlerContext context, ByteBuf message) {
        byte[] body = new byte[message.readableBytes()];
        message.readBytes(body);
        try {
            RpcResponse response = ProtoCodec.decode(body, RpcResponse.class);
            if (response == null) {
                log.warn("empty rpc response received");
                return;
            }
            CompletableFuture<RpcResponse> future = pending.remove(response.getRequestId());
            if (future == null) {
                // 超时已经把这个调用摘走了，响应才姗姗来迟。这是正常的竞态，不是错误
                log.warn("no pending call for requestId={}, it probably timed out", response.getRequestId());
                return;
            }
            future.complete(response);
        } catch (Exception ex) {
            log.warn("bad rpc response: {}", ex.toString());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        log.warn("rpc client channel error: {}", cause.toString());
        context.close();
    }

}
