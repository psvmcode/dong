package com.dong.rpc.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个实现类是 RPC 服务提供者。
 *
 * <p>启动完成后由 {@code ServiceExporter} 扫描所有带这个注解的 Bean，
 * 登记到本地服务表（供服务端反射调用）并注册到注册中心（供客户端发现）。
 *
 * <p>依赖方向是刻意设计的：注解与接口都在 rpc 模块里，
 * 业务模块（例如 search）自己写实现类并打上这个注解，
 * 于是「业务依赖基础设施」，rpc 模块不反过来依赖任何业务模块——
 * 否则基础设施一旦引用业务，就再也没法被第二个业务模块复用了。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcService {

    /**
     * 对外暴露的接口。不填时取实现类实现的第一个接口。
     *
     * @return 接口类型
     */
    Class<?> interfaceClass() default void.class;

    /**
     * 服务名。不填时取接口全限定名——调用方手里一定有接口，
     * 用接口名当服务名就不用额外维护一份名字映射。
     *
     * @return 服务名
     */
    String value() default "";

}
