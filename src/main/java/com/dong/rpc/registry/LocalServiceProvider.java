package com.dong.rpc.registry;

import com.dong.rpc.codec.ProtoCodec;
import com.dong.rpc.codec.ProtoCodecException;
import com.dong.rpc.protocol.RpcRequest;
import com.dong.rpc.protocol.RpcResponse;
import com.dong.rpc.protocol.RpcStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本进程暴露的服务表，服务端收到请求后在这里找到目标方法并反射调用。
 *
 * <p>方法匹配先按「方法名 + 参数类型全限定名」精确匹配，
 * 匹配不上再退一步按「方法名 + 参数个数」匹配且要求唯一。
 * 这个退让是有意的：两端接口版本差一点时（比如参数类型改了个包名），
 * 精确匹配会全部失败，而绝大多数情况下同名同参数个数就是同一个方法。
 * 但只退一步——退到「随便挑一个同名方法」就会变成随机调错方法。
 *
 * <p>反射调用的结果一律转成「状态 + 字节」再回给客户端，
 * 不把服务端异常对象本身传回去：客户端不一定有那个异常类，
 * 传过去只会换来一个反序列化失败，把真正的错误信息盖掉。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "dong.rpc", name = "enabled", havingValue = "true")
public class LocalServiceProvider {

    /**
     * 服务名到持有者的映射。
     */
    private final Map<String, Holder> holders = new ConcurrentHashMap<>();

    /**
     * 累计调用次数。
     */
    private final AtomicLong invocations = new AtomicLong();

    /**
     * 累计失败次数。
     */
    private final AtomicLong failures = new AtomicLong();

    /**
     * 暴露一个服务。
     *
     * @param serviceName    服务名
     * @param bean           实现对象
     * @param interfaceClass 对外接口
     */
    public void register(String serviceName, Object bean, Class<?> interfaceClass) {
        Map<String, Method> bySignature = new HashMap<>();
        List<Method> methods = new ArrayList<>();
        for (Method method : interfaceClass.getMethods()) {
            bySignature.put(signature(method.getName(), typeNames(method.getParameterTypes())), method);
            methods.add(method);
        }
        holders.put(serviceName, new Holder(bean, interfaceClass, Map.copyOf(bySignature), List.copyOf(methods)));
        log.info("rpc provider exposed: {} -> {}", serviceName, bean.getClass().getName());
    }

    /**
     * 已暴露的服务名。
     *
     * @return 服务名集合
     */
    public Set<String> serviceNames() {
        return new TreeSet<>(holders.keySet());
    }

    /**
     * 是否暴露了某个服务。
     *
     * @param serviceName 服务名
     * @return 暴露返回 true
     */
    public boolean contains(String serviceName) {
        return holders.containsKey(serviceName);
    }

    /**
     * 累计调用次数。
     *
     * @return 调用次数
     */
    public long invocations() {
        return invocations.get();
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
     * 就地执行一次调用，返回响应。所有异常都在这里收敛成状态，不往外抛。
     *
     * @param request 请求
     * @return 响应
     */
    public RpcResponse invoke(RpcRequest request) {
        invocations.incrementAndGet();
        long started = System.nanoTime();
        Holder holder = holders.get(request.getServiceName());
        if (holder == null) {
            failures.incrementAndGet();
            return RpcResponse.fail(request.getRequestId(), RpcStatus.SERVICE_NOT_FOUND, "no provider for " + request.getServiceName());
        }
        Method method = match(holder, request);
        if (method == null) {
            failures.incrementAndGet();
            return RpcResponse.fail(request.getRequestId(), RpcStatus.METHOD_NOT_FOUND, request.getMethodName() + " is not exposed by " + request.getServiceName());
        }
        try {
            Object[] arguments = decodeArguments(request, method);
            Object result = method.invoke(holder.bean(), arguments);
            return RpcResponse.ok(request.getRequestId(), ProtoCodec.encodeValue(result), elapsedMillis(started));
        } catch (InvocationTargetException ex) {
            failures.incrementAndGet();
            Throwable cause = ex.getTargetException() == null ? ex : ex.getTargetException();
            log.warn("rpc invocation failed: {}#{} -> {}", request.getServiceName(), request.getMethodName(), cause.toString());
            return RpcResponse.fail(request.getRequestId(), RpcStatus.SERVER_ERROR, cause.toString());
        } catch (ProtoCodecException ex) {
            failures.incrementAndGet();
            return RpcResponse.fail(request.getRequestId(), RpcStatus.CODEC_ERROR, ex.getMessage());
        } catch (Exception ex) {
            failures.incrementAndGet();
            return RpcResponse.fail(request.getRequestId(), RpcStatus.SERVER_ERROR, ex.getMessage());
        }
    }

    /**
     * 匹配目标方法。
     *
     * @param holder  服务持有者
     * @param request 请求
     * @return 方法，找不到返回 null
     */
    private Method match(Holder holder, RpcRequest request) {
        List<String> types = request.getParameterTypes() == null ? List.of() : request.getParameterTypes();
        Method exact = holder.bySignature().get(signature(request.getMethodName(), types));
        if (exact != null) {
            return exact;
        }
        List<Method> candidates = holder.methods().stream().filter(method -> method.getName().equals(request.getMethodName()) && method.getParameterCount() == types.size()).toList();
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    /**
     * 解码参数。按服务端方法的声明类型解码，而不是按客户端传来的类型名加载类——
     * 客户端的类路径与服务端未必一致，而服务端自己的类型一定是能加载的。
     *
     * @param request 请求
     * @param method  目标方法
     * @return 参数数组
     */
    private Object[] decodeArguments(RpcRequest request, Method method) {
        Class<?>[] types = method.getParameterTypes();
        List<byte[]> raw = request.getArguments() == null ? List.of() : request.getArguments();
        if (types.length != raw.size()) {
            throw new ProtoCodecException("argument count mismatch: expected " + types.length + " but got " + raw.size());
        }
        Object[] values = new Object[types.length];
        for (int index = 0; index < types.length; index++) {
            values[index] = ProtoCodec.decodeValue(raw.get(index), types[index]);
        }
        return values;
    }

    /**
     * 方法签名。
     *
     * @param methodName 方法名
     * @param types      参数类型名
     * @return 签名
     */
    private static String signature(String methodName, List<String> types) {
        return methodName + "(" + String.join(",", types) + ")";
    }

    /**
     * 参数类型名列表。
     *
     * @param types 参数类型
     * @return 类型名列表
     */
    private static List<String> typeNames(Class<?>[] types) {
        List<String> names = new ArrayList<>(types.length);
        for (Class<?> type : types) {
            names.add(type.getName());
        }
        return names;
    }

    /**
     * 耗时，毫秒。
     *
     * @param startedNanos 起始纳秒
     * @return 毫秒
     */
    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    /**
     * 一个服务的持有者。
     *
     * @param bean          实现对象
     * @param interfaceName 对外接口名
     * @param bySignature   签名到方法的映射
     * @param methods       全部方法
     */
    private record Holder(Object bean, Class<?> interfaceName, Map<String, Method> bySignature, List<Method> methods) {
    }

}
