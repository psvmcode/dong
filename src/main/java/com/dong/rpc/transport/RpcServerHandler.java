package com.dong.rpc.transport;

import com.dong.rpc.codec.ProtoCodec;
import com.dong.rpc.protocol.RpcRequest;
import com.dong.rpc.protocol.RpcResponse;
import com.dong.rpc.protocol.RpcStatus;
import com.dong.rpc.registry.LocalServiceProvider;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 服务端处理器：解帧后的字节 → 请求 → 本地调用 → 响应。
 *
 * <p>处理器标了 {@code @Sharable}，所有连接共用同一个实例：
 * 它自己不持有任何连接相关的状态，状态全在 LocalServiceProvider 里，
 * 每个连接都新建一个处理器纯属浪费。
 *
 * <p>任何异常都在这里转成响应写回去，绝不往外抛：
 * netty 的异常会沿着 pipeline 走到 exceptionCaught，而那里只能关连接——
 * 客户端拿到的就不是「这次调用失败」，而是「连接断了」，错误信息全丢。
 */
@Slf4j
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class RpcServerHandler extends SimpleChannelInboundHandler<ByteBuf> {

    /**
     * provider，本地服务表。
     */
    private final LocalServiceProvider provider;

    @Override
    protected void channelRead0(ChannelHandlerContext context, ByteBuf message) {
        byte[] body = new byte[message.readableBytes()];
        message.readBytes(body);
        long requestId = -1L;
        try {
            RpcRequest request = ProtoCodec.decode(body, RpcRequest.class);
            if (request == null) {
                write(context, RpcResponse.fail(requestId, RpcStatus.CODEC_ERROR, "empty request body"));
                return;
            }
            requestId = request.getRequestId();
            write(context, provider.invoke(request));
        } catch (Exception ex) {
            log.warn("bad rpc request: {}", ex.toString());
            write(context, RpcResponse.fail(requestId, RpcStatus.CODEC_ERROR, ex.getMessage()));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        log.warn("rpc channel error: {}", cause.toString());
        context.close();
    }

    /**
     * 写回响应。
     *
     * @param context  上下文
     * @param response 响应
     */
    private void write(ChannelHandlerContext context, RpcResponse response) {
        context.writeAndFlush(Unpooled.wrappedBuffer(ProtoCodec.encode(response)));
    }

}
