package com.dong.lab.framework.limiter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

    String key();

    long limit() default 100;

    long window() default 1;

    TimeUnit unit() default TimeUnit.MINUTES;

    RateLimitAlgorithm algorithm() default RateLimitAlgorithm.TOKEN_BUCKET;

    boolean distributed() default true;

    String message() default "";

}
