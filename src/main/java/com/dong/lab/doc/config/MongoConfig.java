package com.dong.lab.doc.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.util.concurrent.TimeUnit;
/**
 * MongoDB 配置类。
 */
@Configuration
@ConditionalOnProperty(prefix = "lab.mongodb", name = "enabled", havingValue = "true")
@EnableMongoRepositories(basePackages = "com.dong.lab.doc.repository")

public class MongoConfig {

    /**
     * MongoDB 连接地址。
     */
    @Value("${spring.data.mongodb.uri:mongodb://127.0.0.1:27017/dong_lab}")
    private String uri;

    /**
     * 创建 MongoDB 客户端。
     *
     * @return MongoDB 客户端
     */
    @Bean(destroyMethod = "close")
    public MongoClient mongoClient() {
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri))
                .applyToSocketSettings(builder -> builder
                        .connectTimeout(5, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS))
                .applyToConnectionPoolSettings(builder -> builder.maxSize(50))
                .build();
        return MongoClients.create(settings);
    }

    /**
     * 创建 MongoTemplate，并配置点号替换规则以支持带点的字段键。
     *
     * @param mongoClient MongoDB 客户端
     * @return MongoTemplate
     */
    @Bean
    public MongoTemplate mongoTemplate(MongoClient mongoClient) {
        String database = new ConnectionString(uri).getDatabase();
        MongoTemplate template = new MongoTemplate(mongoClient, database == null ? "dong_lab" : database);
        MappingMongoConverter converter = (MappingMongoConverter) template.getConverter();
        converter.setMapKeyDotReplacement("__");
        converter.afterPropertiesSet();
        return template;
    }

}
