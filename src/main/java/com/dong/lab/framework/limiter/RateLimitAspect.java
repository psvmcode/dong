package com.dong.lab.framework.limiter;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
/**
 * {@code @RateLimited} 注解切面。在方法执行前尝试获取配额，
 * 拿不到就抛业务异常，由全局异常处理器转成 429 对应的错误码。
 *
 * <p>key 支持 SpEL，可以按业务维度限流而不是所有请求共用一个桶，
 * 例如把活动 id 拼进 key，写法参考 RateLimited 注解的 key 属性。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor

public class RateLimitAspect {

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    /**
     * rateLimitManager。
     */
    private final RateLimitManager rateLimitManager;

    /**
     * around。
     */
    @Around("@annotation(rateLimited)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String key = resolveKey(rateLimited.key(), method, joinPoint.getArgs());
        RateLimitRule rule = new RateLimitRule(rateLimited.limit(),
                Duration.ofNanos(rateLimited.unit().toNanos(rateLimited.window())),
                rateLimited.algorithm());
        boolean allowed = rateLimitManager.tryAcquire(key, rule, rateLimited.distributed());
        if (!allowed) {
            String detail = rateLimited.message().isBlank()
                    ? "rate limit exceeded on key " + key
                    : rateLimited.message();
            log.info("rate limit rejected key={} rule={}", key, rule);
            throw new BusinessException(Constants.CODE_TOO_MANY_REQUESTS, detail);
        }

        return joinPoint.proceed();
    }

    /**
     * resolveKey。
     */
    private String resolveKey(String expression, Method method, Object[] args) {
        if (!expression.contains("#") && !expression.contains("'")) {
            return expression;
        }
        EvaluationContext context = new StandardEvaluationContext();
        String[] parameterNames = NAME_DISCOVERER.getParameterNames(method);
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }
        return PARSER.parseExpression(expression).getValue(context, String.class);
    }

}
