package com.dong.lab.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * OpenAPI 文档配置类。
 */
@Configuration

public class OpenApiConfig {

    /**
     * 创建 dong-lab OpenAPI 文档对象。
     *
     * @return OpenAPI 对象
     */
    @Bean
    public OpenAPI dongLabOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("dong-lab api")
                        .description("middleware and distributed scenario laboratory")
                        .version("1.0.0")
                        .license(new License().name("MIT")));
    }

}
