package com.dong.cache.support;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.common.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 缓存模块的读防护拦截器。把限流放在拦截器而不是 controller 里，
 * 是为了保证每个接口都被覆盖——手写调用迟早会漏掉一两个新加的接口。
 *
 * <p>未命中计数放在 afterCompletion 而不是业务代码里：
 * 业务层不该关心请求来自哪个 IP，它只负责回答「有没有这条数据」。
 * 这里按异常码判断，1001（数据不存在）才算一次未命中，
 * 1005（依赖不可用）不算，否则下游一抖动就会把正常用户封掉。
 */
@Component
@RequiredArgsConstructor
public class CacheGuardInterceptor implements HandlerInterceptor {

    /**
     * 缓存读防护组件。
     */
    private final CacheReadGuard cacheReadGuard;

    /**
     * 客户端 IP 解析器。
     */
    private final ClientIpResolver clientIpResolver;

    /**
     * 请求进入业务前做封禁检查与限流。
     *
     * @param request  当前请求
     * @param response 当前响应
     * @param handler  目标处理器
     * @return 放行返回 true
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String clientIp = clientIpResolver.resolve(request);
        cacheReadGuard.checkNotBlocked(clientIp);
        if (isHeavy(request)) {
            cacheReadGuard.checkHeavy(clientIp);
        } else {
            cacheReadGuard.checkRead(clientIp);
        }
        return true;
    }

    /**
     * 请求结束后统计未命中，用于识别撞 id 的扫描行为。
     *
     * @param request  当前请求
     * @param response 当前响应
     * @param handler  目标处理器
     * @param ex       业务抛出的异常，可能为 null
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (ex instanceof BusinessException businessException && businessException.getCode() == Constants.CODE_DATA_NOT_FOUND) {
            cacheReadGuard.recordMiss(clientIpResolver.resolve(request));
        }
    }

    /**
     * 判断是否重量级请求。写操作与全量、预热、压测类接口都按重接口对待：
     * 它们单次成本远高于一次普通读，配额必须单独收紧。
     *
     * @param request 当前请求
     * @return 是否重量级
     */
    private boolean isHeavy(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri.endsWith("/warm-up") || uri.contains("/penetration") || uri.endsWith("/all");
    }

}
