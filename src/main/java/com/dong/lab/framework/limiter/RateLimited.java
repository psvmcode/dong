package com.dong.lab.framework.limiter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 方法级限流注解。标注在需要限流的方法上，由 RateLimitAspect 拦截。
 *
 * <p>key 支持 SpEL，可以引用方法参数实现按业务维度限流；
 * 不写表达式则按字面量作为固定 key，所有请求共用一个桶。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

    /**
     * 限流键，支持 SpEL 表达式。
     */
    String key();

    /**
     * 窗口内允许通过的次数。
     */
    long limit() default 100;

    /**
     * 窗口大小，配合 unit 使用。
     */
    long window() default 1;

    TimeUnit unit() default TimeUnit.MINUTES;

    /**
     * 算法。默认令牌桶，它允许一定突发，对多数接口更友好。
     */
    RateLimitAlgorithm algorithm() default RateLimitAlgorithm.TOKEN_BUCKET;

    /**
     * 是否走分布式实现。多实例部署必须为 true，
     * 否则每个节点各自计数，实际放放量会是配额乘以节点数。
     */
    boolean distributed() default true;

    /**
     * 被限流时的提示，留空则使用默认文案。
     */
    String message() default "";

}
