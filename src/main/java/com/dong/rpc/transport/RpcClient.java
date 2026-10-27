package com.dong.rpc.transport;

import com.dong.rpc.codec.ProtoCodec;
import com.dong.rpc.protocol.RpcRequest;
import com.dong.rpc.protocol.RpcResponse;
import com.dong.rpc.protocol.RpcStatus;
import com.dong.rpc.registry.ServiceInstance;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.util.concurrent.DefaultThreadFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RPC 客户端：一条地址一条长连接，请求发出后挂起等待响应。
 *
 * <p>为什么按地址缓存长连接而不是每次新建：TCP 握手加慢启动的代价是毫秒级，
 * 而一次 RPC 调用本身可能只有几百微秒——连接不复用的话，握手会比调用还贵。
 * 取用前检查 isActive，断了就重连。
 *
 * <p><b>超时是必需的</b>，哪怕没做重试与熔断：没有超时的同步调用会一直占着线程，
 * 对端一挂，调用方这边就是一堆永远醒不过来的请求。超时后从 pending 里摘掉，
 * 迟到的响应回来时找不到调用者，只能记一条 warn——这是能接受的代价。
 *
 * <p>调用失败返回带状态的响应而不是抛异常：让调用方（动态代理）统一决定
 * 「服务不存在」和「服务端报错」分别怎么翻译成业务错误码。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "dong.rpc", name = "enabled", havingValue = "true")
public class RpcClient {

    /**
     * 地址到连接的缓存。
     */
    private final Map<String, Channel> channels = new ConcurrentHashMap<>();

    /**
     * 请求 id 到挂起调用的映射。
     */
    private final Map<Long, CompletableFuture<RpcResponse>> pending = new ConcurrentHashMap<>();

    /**
     * 请求 id 序列。
     */
    private final AtomicLong sequence = new AtomicLong();

    /**
     * 累计调用次数。
     */
    private final AtomicLong requests = new AtomicLong();

    /**
     * 累计失败次数。
     */
    private final AtomicLong failures = new AtomicLong();

    /**
     * 累计耗时，毫秒。
     */
    private final AtomicLong totalElapsed = new AtomicLong();

    /**
     * 客户端线程组。
     */
    private EventLoopGroup group;

    /**
     * 连接引导器。
     */
    private Bootstrap bootstrap;

    /**
     * 连接超时，毫秒。
     */
    @Value("${dong.rpc.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    /**
     * 调用超时，毫秒。
     */
    @Value("${dong.rpc.call-timeout-ms:3000}")
    private long callTimeoutMs;

    /**
     * 单帧最大长度，字节。
     */
    @Value("${dong.rpc.max-frame-length:10485760}")
    private int maxFrameLength;

    /**
     * 初始化引导器。
     */
    @PostConstruct
    public void init() {
        group = new NioEventLoopGroup(0, new DefaultThreadFactory("rpc-client"));
        this.bootstrap = new Bootstrap().group(group).channel(NioSocketChannel.class).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs).option(ChannelOption.SO_KEEPALIVE, true).handler(new ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(SocketChannel socketChannel) {
                socketChannel.pipeline().addLast(new LengthFieldBasedFrameDecoder(maxFrameLength, 0, 4, 0, 4));
                socketChannel.pipeline().addLast(new LengthFieldPrepender(4));
                socketChannel.pipeline().addLast(new RpcClientHandler(pending));
            }

        });
    }

    /**
     * 关闭全部连接。
     */
    @PreDestroy
    public void close() {
        pending.clear();
        channels.values().forEach(Channel::close);
        channels.clear();
        if (group != null) {
            group.shutdownGracefully();
        }
        log.info("rpc client closed");
    }

    /**
     * 生成一个请求 id。
     *
     * @return 请求 id
     */
    public long nextRequestId() {
        return sequence.incrementAndGet();
    }

    /**
     * 发起一次调用，阻塞等待响应或超时。
     *
     * @param instance 目标实例
     * @param request  请求
     * @return 响应，超时或失败时是带状态的响应
     */
    public RpcResponse send(ServiceInstance instance, RpcRequest request) {
        long started = System.currentTimeMillis();
        requests.incrementAndGet();
        CompletableFuture<RpcResponse> future = new CompletableFuture<>();
        pending.put(request.getRequestId(), future);
        try {
            Channel channel = channel(instance);
            channel.writeAndFlush(Unpooled.wrappedBuffer(ProtoCodec.encode(request)));
            RpcResponse response = future.get(callTimeoutMs, TimeUnit.MILLISECONDS);
            totalElapsed.addAndGet(System.currentTimeMillis() - started);
            return response;
        } catch (TimeoutException ex) {
            failures.incrementAndGet();
            return RpcResponse.fail(request.getRequestId(), RpcStatus.TIMEOUT, "no response within " + callTimeoutMs + "ms from " + instance.address());
        } catch (Exception ex) {
            failures.incrementAndGet();
            return RpcResponse.fail(request.getRequestId(), RpcStatus.SERVER_ERROR, ex.getMessage());
        } finally {
            // 超时后必须摘掉，否则迟到的响应会一直堆在 map 里
            pending.remove(request.getRequestId());
        }
    }

    /**
     * 累计调用次数。
     *
     * @return 调用次数
     */
    public long requests() {
        return requests.get();
    }

    /**
     * 累计失败次数。
     *
     * @return 失败次数
     */
    public long failures() {
        return failures.get();
    }

    /**
     * 平均耗时，毫秒。
     *
     * @return 平均耗时
     */
    public long averageElapsed() {
        long success = requests.get() - failures.get();
        return success <= 0 ? 0L : totalElapsed.get() / success;
    }

    /**
     * 取一条可用连接，断了就重连。
     *
     * @param instance 实例
     * @return 连接
     */
    private Channel channel(ServiceInstance instance) {
        String address = instance.address();
        Channel existing = channels.get(address);
        if (existing != null && existing.isActive()) {
            return existing;
        }
        Channel created = connect(instance);
        channels.put(address, created);
        return created;
    }

    /**
     * 建立连接。
     *
     * @param instance 实例
     * @return 连接
     */
    private Channel connect(ServiceInstance instance) {
        try {
            Channel channel = bootstrap.connect(instance.getHost(), instance.getPort()).sync().channel();
            log.info("rpc client connected to {}", instance.address());
            return channel;
        } catch (Exception ex) {
            throw new IllegalStateException("connect to " + instance.address() + " failed: " + ex.getMessage(), ex);
        }
    }

}
