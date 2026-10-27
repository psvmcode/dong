package com.dong.rpc.transport;

import com.dong.rpc.registry.LocalServiceProvider;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.util.concurrent.DefaultThreadFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RPC 服务端，监听独立端口，帧格式是「4 字节长度 + protobuf 报文」。
 *
 * <p>长度前缀不是可选项：TCP 是字节流，没有消息边界，
 * 一次 write 可能被拆成多次 read，两次 write 也可能被合并成一次 read——
 * 这就是粘包拆包。LengthField 编解码器按长度切帧，业务处理器拿到的永远是一整条消息。
 *
 * <p>最大帧长必须设上限：不设的话对端只要不停发数据，服务端就会一直攒内存直到 OOM。
 * 上限即是一种自我保护。
 *
 * <p><b>绑定失败不阻断应用启动</b>：端口被占用这种事不该让整个应用起不来，
 * 就像日志模块不会因为 ES 不可用而拒绝启动。启动失败只记 error，
 * 调用方随后会在「服务发现不到实例」这一步得到明确反馈。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dong.rpc", name = "enabled", havingValue = "true")
public class RpcServer {

    /**
     * provider，本地服务表。
     */
    private final LocalServiceProvider provider;

    /**
     * 监听端口。
     */
    @Value("${dong.rpc.port:9090}")
    private int port;

    /**
     * 单帧最大长度，字节。
     */
    @Value("${dong.rpc.max-frame-length:10485760}")
    private int maxFrameLength;

    /**
     * 接收连接的线程数，单端口一个足够。
     */
    @Value("${dong.rpc.boss-threads:1}")
    private int bossThreads;

    /**
     * 处理 IO 的线程数，0 表示交给 netty 按 CPU 核数决定。
     */
    @Value("${dong.rpc.worker-threads:0}")
    private int workerThreads;

    /**
     * 接收连接线程组。
     */
    private EventLoopGroup bossGroup;

    /**
     * 处理 IO 线程组。
     */
    private EventLoopGroup workerGroup;

    /**
     * 监听通道。
     */
    private Channel channel;

    /**
     * 是否在监听。
     */
    private volatile boolean running;

    /**
     * 启动监听。
     */
    @PostConstruct
    public void start() {
        bossGroup = new NioEventLoopGroup(bossThreads, new DefaultThreadFactory("rpc-boss"));
        workerGroup = new NioEventLoopGroup(workerThreads, new DefaultThreadFactory("rpc-worker"));
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).option(ChannelOption.SO_BACKLOG, 128).childOption(ChannelOption.SO_KEEPALIVE, true).childHandler(new ChannelInitializer<SocketChannel>() {

                @Override
                protected void initChannel(SocketChannel socketChannel) {
                    socketChannel.pipeline().addLast(new LengthFieldBasedFrameDecoder(maxFrameLength, 0, 4, 0, 4));
                    socketChannel.pipeline().addLast(new LengthFieldPrepender(4));
                    socketChannel.pipeline().addLast(new RpcServerHandler(provider));
                }

            });
            channel = bootstrap.bind(port).sync().channel();
            running = true;
            log.info("rpc server listening on port {}", port);
        } catch (Exception ex) {
            running = false;
            log.error("rpc server failed to bind port {}: {}", port, ex.toString());
            shutdownGroups();
        }
    }

    /**
     * 停止监听。
     */
    @PreDestroy
    public void stop() {
        if (channel != null) {
            channel.close();
        }
        shutdownGroups();
        running = false;
        log.info("rpc server stopped");
    }

    /**
     * 是否在监听。
     *
     * @return 监听中返回 true
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 监听端口。
     *
     * @return 端口
     */
    public int getPort() {
        return port;
    }

    /**
     * 关闭线程组。用优雅关闭而不是立刻 shutdown：
     * 正在处理的请求不该被硬生生掐断。
     */
    private void shutdownGroups() {
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
    }

}
