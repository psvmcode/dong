package com.dong.lab.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
/**
 * Swagger UI 与 Knife4j 地址打印配置类。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor

public class SwaggerUiConfig implements WebMvcConfigurer {

    private static final String WEBJAR_VERSION = "5.32.11";

    private static final String WEBJAR_CSS = "/META-INF/resources/webjars/swagger-ui/" + WEBJAR_VERSION + "/swagger-ui.css";

    private static final String SWAGGER_WEB_MVC_CONFIGURER = "swaggerWebMvcConfigurer";

    /**
     * 运行环境，用于读取服务端口。
     */
    private final Environment environment;

    /**
     * 移除 springdoc 默认注册的 swagger WebMvcConfigurer，避免与自定义配置冲突。
     *
     * @return BeanDefinitionRegistryPostProcessor
     */
    @Bean
    public static BeanDefinitionRegistryPostProcessor springdocResourceConfigurerRemover() {
        return registry -> {
            if (registry.containsBeanDefinition(SWAGGER_WEB_MVC_CONFIGURER)) {
                registry.removeBeanDefinition(SWAGGER_WEB_MVC_CONFIGURER);
            }
        };
    }

    /**
     * 添加 Swagger UI 重定向视图。
     *
     * @param registry 视图控制器注册表
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/swagger-ui.html", "/swagger-ui/index.html");
    }

    /**
     * 应用启动后打印 Swagger UI、Knife4j 与 Actuator 访问地址。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void logEndpoints() {
        String port = environment.getProperty("local.server.port", "8090");
        String host = "http://127.0.0.1:" + port;
        log.info("------------------------------------------------------------");
        log.info("knife4j ui    {}", host + "/doc.html");
        log.info("swagger ui    {}", host + "/swagger-ui/index.html");
        log.info("swagger short {}", host + "/swagger-ui.html");
        log.info("openapi json  {}", host + "/v3/api-docs");
        log.info("actuator      {}", host + "/actuator/health");
        log.info("------------------------------------------------------------");
        if (getClass().getResource(WEBJAR_CSS) == null) {
            log.warn("swagger ui webjar {} is missing, update the version in static/swagger-ui/index.html", WEBJAR_VERSION);
        }
    }
}
