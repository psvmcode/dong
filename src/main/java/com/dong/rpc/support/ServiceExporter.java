package com.dong.rpc.support;

import com.dong.rpc.annotation.RpcService;
import com.dong.rpc.registry.LocalServiceProvider;
import com.dong.rpc.registry.ServiceInstance;
import com.dong.rpc.registry.ServiceRegistry;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 扫描并把带 {@link RpcService} 的实现暴露出去。
 *
 * <p>监听 ContextRefreshedEvent 而不是用 @PostConstruct：
 * 服务 Bean 的注入依赖必须全部就绪才能登记，否则在服务表里放进去一个半成品，
 * 调用时才炸，排查成本高得多。
 *
 * <p>服务名默认取接口全限定名而不是实现类名：调用方手里一定有接口，
 * 用接口名就不需要维护一份「名字 → 接口」的映射表；
 * 而实现类名会重构、会换包，拿它当契约迟早出错。
 *
 * <p>注册到注册中心的地址默认是本机 IP 而不是 localhost：
 * 注册中心存在的意义就是让别的机器能连过来，写死 localhost 等于只对本机有效，
 * 这在单机时看不出问题，一上多机就是「发现成功却连不上」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dong.rpc", name = "enabled", havingValue = "true")
public class ServiceExporter implements ApplicationListener<ContextRefreshedEvent> {

    /**
     * 本机地址，启动期解析一次。
     */
    private static final String LOCAL_HOST = resolveHost();

    /**
     * applicationContext，应用上下文。
     */
    private final ApplicationContext applicationContext;

    /**
     * provider，本地服务表。
     */
    private final LocalServiceProvider provider;

    /**
     * registry，注册中心。
     */
    private final ServiceRegistry registry;

    /**
     * 监听端口，注册时一起写进地址里。
     */
    @Value("${dong.rpc.port:9090}")
    private int port;

    /**
     * 注册用的主机地址。留空时取本机地址；多网卡或容器里通常需要显式指定。
     */
    @Value("${dong.rpc.register-host:}")
    private String registerHost;

    /**
     * 本进程已暴露的实例。
     */
    private final List<ServiceInstance> instances = new CopyOnWriteArrayList<>();

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(RpcService.class);
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Object bean = entry.getValue();
            // 从 Bean 定义上取注解而不是从 getClass 上取：Bean 可能被 AOP 代理，
            // 代理类的注解取不到，表现为「打了注解却没被暴露」
            RpcService annotation = applicationContext.findAnnotationOnBean(entry.getKey(), RpcService.class);
            if (annotation == null) {
                continue;
            }
            Class<?> interfaceClass = interfaceOf(annotation, bean);
            if (interfaceClass == null) {
                log.warn("rpc service {} declares no interface, skipped", entry.getKey());
                continue;
            }
            String serviceName = annotation.value().isBlank() ? interfaceClass.getName() : annotation.value();
            provider.register(serviceName, bean, interfaceClass);
            ServiceInstance instance = new ServiceInstance(serviceName, host(), port);
            instances.add(instance);
            registry.register(instance);
        }
        log.info("rpc exporter finished: {} services exposed", instances.size());
    }

    /**
     * 正常停机时摘掉自己的注册。
     */
    @PreDestroy
    public void unregisterAll() {
        instances.forEach(registry::unregister);
        instances.clear();
    }

    /**
     * 本进程已暴露的实例。
     *
     * @return 实例列表
     */
    public List<ServiceInstance> instances() {
        return List.copyOf(instances);
    }

    /**
     * 注册用的主机地址。
     *
     * @return 主机地址
     */
    private String host() {
        return registerHost == null || registerHost.isBlank() ? LOCAL_HOST : registerHost.trim();
    }

    /**
     * 决定对外接口：注解里写了就用注解的，否则取实现类实现的第一个接口。
     *
     * @param annotation 服务注解
     * @param bean       实现对象
     * @return 接口，取不到返回 null
     */
    private Class<?> interfaceOf(RpcService annotation, Object bean) {
        if (annotation.interfaceClass() != void.class) {
            return annotation.interfaceClass();
        }
        Class<?>[] interfaces = bean.getClass().getInterfaces();
        return interfaces.length == 0 ? null : interfaces[0];
    }

    /**
     * 解析本机地址。
     *
     * @return 主机地址，取不到返回 127.0.0.1
     */
    private static String resolveHost() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ex) {
            return "127.0.0.1";
        }
    }

}
