package com.dong;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ReactiveElasticsearchClientAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 实验室应用启动类。扫描所有 Mapper 接口并启用 Spring Boot 自动配置。
 *
 * <p>ES 的 RestClient 与 ElasticsearchClient 已改为 Boot 自动配置（spring.elasticsearch.*），
 * 所以不再排除 ElasticsearchRestClientAutoConfiguration 与 ElasticsearchClientAutoConfiguration。
 *
 * <p>剩下的排除项：Mongo 的客户端与仓储层由 doc/MongoConfig 自己装配；
 * Elasticsearch 只用 Java API Client，不引入 Spring Data 的模版层、仓储层与响应式客户端。
 */
@EnableScheduling
@SpringBootApplication(exclude = {
        MongoAutoConfiguration.class,
        MongoDataAutoConfiguration.class,
        MongoRepositoriesAutoConfiguration.class,
        ElasticsearchDataAutoConfiguration.class,
        ElasticsearchRepositoriesAutoConfiguration.class,
        ReactiveElasticsearchRepositoriesAutoConfiguration.class,
        ReactiveElasticsearchClientAutoConfiguration.class
})
public class DongApplication {

    /**
     * 应用入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DongApplication.class, args);
    }

}
