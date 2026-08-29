package com.dong.lab.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

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
