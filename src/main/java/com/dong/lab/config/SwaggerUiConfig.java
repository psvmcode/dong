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

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SwaggerUiConfig implements WebMvcConfigurer {

    private static final String WEBJAR_VERSION = "5.32.11";

    private static final String WEBJAR_CSS = "/META-INF/resources/webjars/swagger-ui/" + WEBJAR_VERSION + "/swagger-ui.css";

    private static final String SWAGGER_WEB_MVC_CONFIGURER = "swaggerWebMvcConfigurer";

    private final Environment environment;

    @Bean
    public static BeanDefinitionRegistryPostProcessor springdocResourceConfigurerRemover() {
        return registry -> {
            if (registry.containsBeanDefinition(SWAGGER_WEB_MVC_CONFIGURER)) {
                registry.removeBeanDefinition(SWAGGER_WEB_MVC_CONFIGURER);
            }
        };
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/swagger-ui.html", "/swagger-ui/index.html");
    }

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
