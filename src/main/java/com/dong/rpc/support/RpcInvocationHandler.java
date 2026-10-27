package com.dong.rpc.support;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.rpc.codec.ProtoCodec;
import com.dong.rpc.protocol.RpcRequest;
import com.dong.rpc.protocol.RpcResponse;
import com.dong.rpc.protocol.RpcStatus;
import com.dong.rpc.registry.ServiceDiscovery;
import com.dong.rpc.registry.ServiceInstance;
import com.dong.rpc.transport.RpcClient;
import lombok.RequiredArgsConstructor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 客户端动态代理的调用逻辑：把一次 Java 方法调用翻译成一次 RPC。
 *
 * <p>调用方写的是 {@code echoService.echo("hi")}，编译器看到的是普通接口调用；
 * 真正的网络往返藏在这个 handler 里——这就是 RPC 想达到的效果：
 * 远程调用与本地调用在源码层面没有区别，代价是失败模式完全不同，
 * 所以超时、服务不存在这些必须翻译成明确的错误码，不能像本地调用那样靠异常类名区分。
 *
 * <p>负载均衡在这里用最简单的随机：实例从注册中心取回来时已经是「TTL 内活着的」，
 * 随机足够均匀，且不需要维护轮询状态（无状态才能并发安全）。
 */
@RequiredArgsConstructor
public class RpcInvocationHandler implements InvocationHandler {

    /**
     * 被调用的接口。
     */
    private final Class<?> interfaceClass;

    /**
     * client，RPC 客户端。
     */
    private final RpcClient client;

    /**
     * discovery，服务发现。
     */
    private final ServiceDiscovery discovery;

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        if (method.getDeclaringClass() == Object.class) {
            return localObjectMethod(proxy, method, args);
        }
        String serviceName = interfaceClass.getName();
        List<ServiceInstance> instances = discovery.discover(serviceName);
        if (instances.isEmpty()) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "no available provider for " + serviceName);
        }
        ServiceInstance instance = instances.get(ThreadLocalRandom.current().nextInt(instances.size()));
        RpcRequest request = new RpcRequest();
        request.setRequestId(client.nextRequestId());
        request.setServiceName(serviceName);
        request.setMethodName(method.getName());
        request.setParameterTypes(typeNames(method.getParameterTypes()));
        request.setArguments(encodeArguments(method, args));
        request.setSentAt(System.currentTimeMillis());
        RpcResponse response = client.send(instance, request);
        RpcStatus status = response.statusEnum();
        if (status != RpcStatus.OK) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "rpc " + status.getText() + ": " + response.getMessage());
        }
        if (method.getReturnType() == void.class) {
            return null;
        }
        return ProtoCodec.decodeValue(response.getPayload(), method.getReturnType());
    }

    /**
     * 处理 Object 上的方法。toString 这类不该发到网络上，
     * 否则一次日志打印就变成一次 RPC 调用。
     *
     * @param proxy  代理对象
     * @param method 方法
     * @param args   参数
     * @return 结果
     */
    private Object localObjectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "rpc proxy for " + interfaceClass.getName();
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new IllegalStateException("unexpected object method " + method.getName());
        };
    }

    /**
     * 编码参数。
     *
     * @param method 方法
     * @param args   实参
     * @return 参数字节列表
     */
    private List<byte[]> encodeArguments(Method method, Object[] args) {
        Class<?>[] types = method.getParameterTypes();
        List<byte[]> encoded = new ArrayList<>(types.length);
        for (int index = 0; index < types.length; index++) {
            Object value = args == null ? null : args[index];
            encoded.add(ProtoCodec.encodeValue(value));
        }
        return encoded;
    }

    /**
     * 参数类型名列表。
     *
     * @param types 参数类型
     * @return 类型名列表
     */
    private List<String> typeNames(Class<?>[] types) {
        List<String> names = new ArrayList<>(types.length);
        for (Class<?> type : types) {
            names.add(type.getName());
        }
        return names;
    }

}
