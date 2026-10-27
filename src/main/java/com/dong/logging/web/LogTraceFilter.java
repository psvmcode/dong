package com.dong.logging.web;

import com.dong.logging.support.LogTraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 给每个请求注入链路追踪 id。
 *
 * <p>traceId 是日志检索里最有用的一个维度：出了问题先按它把一次请求的全部日志捞出来，
 * 比在时间窗口里按关键字猜快得多。没有它，一次请求打出的十几条日志
 * 在按时间排序的结果里会被别的请求的日志冲散。
 *
 * <p>上游传来的 X-Trace-Id 直接沿用：链路要能跨服务串起来，
 * 每个服务各生成一个等于把链路切断。但要清洗——它是外部输入，
 * 不清洗就会被写进索引，一个超长或者带控制字符的值能污染整个字段。
 *
 * <p>放在最前面（优先级仅次于框架内部过滤器）：越早注入，
 * 后面的拦截器、参数校验、异常处理打的日志才都带着它。
 */
@Component
@ConditionalOnProperty(prefix = "dong.logging", name = "enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class LogTraceFilter extends OncePerRequestFilter {

    /**
     * 追踪 id 的请求与响应头。
     */
    public static final String TRACE_HEADER = "X-Trace-Id";

    /**
     * 追踪 id 长度上限。
     */
    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String traceId = resolve(request.getHeader(TRACE_HEADER));
        LogTraceContext.put(traceId);
        try {
            response.setHeader(TRACE_HEADER, traceId);
            chain.doFilter(request, response);
        } finally {
            LogTraceContext.clear();
        }
    }

    /**
     * 决定用哪个追踪 id。上游传了就用上游的，否则自己生成一个。
     *
     * @param incoming 上游传来的值
     * @return 追踪 id
     */
    private String resolve(String incoming) {
        if (incoming == null || incoming.isBlank()) {
            return newTraceId();
        }
        String cleaned = incoming.trim().replaceAll("[^A-Za-z0-9._-]", "");
        if (cleaned.isEmpty()) {
            return newTraceId();
        }
        return cleaned.length() > MAX_LENGTH ? cleaned.substring(0, MAX_LENGTH) : cleaned;
    }

    /**
     * 生成追踪 id。12 位十六进制：短到能口头报出来，长到不会撞。
     *
     * @return 追踪 id
     */
    private String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

}
