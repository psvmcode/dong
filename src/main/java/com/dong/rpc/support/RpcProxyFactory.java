package com.dong.rpc.support;

import com.dong.rpc.registry.ServiceDiscovery;
import com.dong.rpc.transport.RpcClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.reflect.Proxy;

/**
 * 生成 RPC 客户端代理。
 *
 * <p>用 JDK 动态代理而不是 CGLib：RPC 客户端代理的一定是接口
 * （调用方手里只有接口，实现类在远端），JDK 代理正好够用，也不需要额外的字节码依赖。
 *
 * <p>代理是「按接口创建」的，通常应该在应用启动时创建一次并缓存起来反复用；
 * 每次调用都新建代理会不断生成新的代理类，堆里的类会越来越多。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dong.rpc", name = "enabled", havingValue = "true")
public class RpcProxyFactory {

    /**
     * client，RPC 客户端。
     */
    private final RpcClient client;

    /**
     * discovery，服务发现。
     */
    private final ServiceDiscovery discovery;

    /**
     * 创建一个远程服务的客户端代理。
     *
     * @param interfaceClass 接口
     * @param <T>            接口类型
     * @return 代理
     */
    @SuppressWarnings("unchecked")
    public <T> T create(Class<T> interfaceClass) {
        return (T) Proxy.newProxyInstance(interfaceClass.getClassLoader(), new Class<?>[]{interfaceClass}, new RpcInvocationHandler(interfaceClass, client, discovery));
    }

}
