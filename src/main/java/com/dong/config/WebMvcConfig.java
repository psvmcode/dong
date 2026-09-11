package com.dong.config;

import com.dong.common.web.GlobalRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
/**
 * Web 层配置。目前只注册全局限流拦截器。
 *
 * <p>只对 /api 下的业务接口生效，不拦 actuator 与静态资源——
 * 健康检查被限流掉会让监控系统误判服务不可用。
 */
@Configuration
@RequiredArgsConstructor

public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 全局限流拦截器。
     */
    private final GlobalRateLimitInterceptor globalRateLimitInterceptor;

    /**
     * 注册拦截器。
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(globalRateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/actuator/**");
    }

}
