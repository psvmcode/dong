package com.dong.config;

import com.dong.cache.support.CacheGuardInterceptor;
import com.dong.common.web.GlobalRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置。注册全局限流与缓存读防护两个拦截器。
 *
 * <p>只对 /api 下的业务接口生效，不拦 actuator 与静态资源——
 * 健康检查被限流掉会让监控系统误判服务不可用。
 *
 * <p>缓存防护单独限定在 /api/cache 下：它的配额比全局限流更紧，
 * 还有扫描封禁这类缓存特有的规则，套到别的模块上会误伤正常流量。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 全局限流拦截器。
     */
    private final GlobalRateLimitInterceptor globalRateLimitInterceptor;

    /**
     * 缓存读防护拦截器。
     */
    private final CacheGuardInterceptor cacheGuardInterceptor;

    /**
     * 注册拦截器。全局在前、模块防护在后，先过粗筛再过细筛。
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(globalRateLimitInterceptor).addPathPatterns("/api/**").excludePathPatterns("/actuator/**");
        registry.addInterceptor(cacheGuardInterceptor).addPathPatterns("/api/cache/**");
    }

}
