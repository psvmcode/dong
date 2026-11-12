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
            byte[] body = ProtoCodec.encode(request);
            if (body.length > maxFrameLength - 4) {
                // 超长帧会被对端 LengthFieldBasedFrameDecoder 判为 TooLongFrameException 并关连接，
                // 该连接上其它并发请求会一起丢失，必须本地先快速失败
                return RpcResponse.fail(request.getRequestId(), RpcStatus.SERVER_ERROR, "request frame exceeds " + maxFrameLength + " bytes");
            }
            Channel channel = channel(instance);
            channel.writeAndFlush(Unpooled.wrappedBuffer(body)).addListener(f -> {
                // 写失败立即失败，不再干等满超时才返回
                if (!f.isSuccess()) {
                    future.completeExceptionally(f.cause());
                }
            });
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
     * 必须用 compute 原子替换：get → 判活 → connect → put 的非原子序列下，
     * 并发重连的两个线程会各自建连，先放入的连接被覆盖且永远不会关闭，
     * 对端重启或网络闪断的高并发期会积累泄漏的 socket 与 EventLoop 注册。
     *
     * @param instance 实例
     * @return 连接
     */
    private Channel channel(ServiceInstance instance) {
        String address = instance.address();
        return channels.compute(address, (key, existing) -> {
            if (existing != null && existing.isActive()) {
                return existing;
            }
            Channel created = connect(instance);
            if (existing != null) {
                existing.close();
            }
            return created;
        });
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
