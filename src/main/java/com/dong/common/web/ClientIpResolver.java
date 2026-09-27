package com.dong.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 客户端 IP 解析。反向代理下优先取 X-Forwarded-For 的第一段，
 * 取不到再看 X-Real-IP，最后回落到 remoteAddr。
 *
 * <p>注意 X-Forwarded-For 是可以伪造的，
 * 生产环境应只在可信网关之后才信任它，否则攻击者换个头就能绕过按 IP 的限流。
 *
 * <p>取不到时返回固定兜底值而不是空串：
 * 空串会让所有取不到 IP 的请求共用同一个限流桶，等于互相牵连。
 */
@Component
public class ClientIpResolver {

    private static final String UNKNOWN = "unknown";

    /**
     * 解析客户端 IP。
     *
     * @param request 当前请求
     * @return 客户端 IP
     */
    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? UNKNOWN : remote;
    }

}
