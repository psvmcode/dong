package com.dong.common.web;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.framework.limiter.RateLimitAlgorithm;
import com.dong.framework.limiter.RateLimitManager;
import com.dong.framework.limiter.RateLimitRule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
/**
 * 全局接口限流。
 *
 * <p>参数校验挡的是「单条请求把系统搞坏」，这一层挡的是「海量请求把系统压垮」。
 * 两者不可替代：参数再合法，一秒钟来十万次也扛不住。
 *
 * <p>默认按客户端 IP 限流，用本地令牌桶而不是分布式：
 * 全局限流是保护性措施，本身不该再依赖 Redis——
 * 否则 Redis 抖动时限流先失效，反而把压力全放进来。
 *
 * <p>被限流的请求返回 1003（too many requests），
 * 与业务限流区分开，便于监控识别。
 */
@Slf4j
@Component
@RequiredArgsConstructor

public class GlobalRateLimitInterceptor implements HandlerInterceptor {

    /**
     * 限流键前缀。
     */
    private static final String PREFIX = "lab:global:rl:";

    /**
     * 限流管理器。
     */
    private final RateLimitManager rateLimitManager;

    /**
     * 是否开启全局限流，默认开启，可在配置里关掉。
     */
    @Value("${dong.global-rate-limit.enabled:true}")
    private boolean enabled;

    /**
     * 时间窗口内允许的请求数，默认每秒 200。
     */
    @Value("${dong.global-rate-limit.permits:200}")
    private long permits;

    /**
     * 窗口时长，单位秒，默认 1 秒。
     */
    @Value("${dong.global-rate-limit.window-seconds:1}")
    private long windowSeconds;

    /**
     * 请求进入业务前的限流判定。
     *
     * @param request  当前请求
     * @param response 当前响应
     * @param handler  目标处理器
     * @return 放行返回 true
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!enabled) {
            return true;
        }
        String key = PREFIX + clientIp(request);
        RateLimitRule rule = new RateLimitRule(permits, Duration.ofSeconds(windowSeconds),
                RateLimitAlgorithm.TOKEN_BUCKET);
        if (!rateLimitManager.tryAcquire(key, rule, false)) {
            log.warn("global rate limit rejected ip={} path={}", clientIp(request), request.getRequestURI());
            throw new BusinessException(Constants.CODE_TOO_MANY_REQUESTS, Constants.MESSAGE_TOO_MANY_REQUESTS);
        }
        return true;
    }

    /**
     * 取客户端 IP。反向代理下优先取 X-Forwarded-For 的第一段，
     * 取不到再回落到 remoteAddr。注意这个头可以伪造，
     * 生产环境应只在可信网关之后才信任它。
     *
     * @param request 当前请求
     * @return 客户端 IP
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return request.getRemoteAddr();
    }

}
