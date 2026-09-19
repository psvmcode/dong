package com.dong.search.config;

import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.elasticsearch.RestClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 客户端配置。
 *
 * <p>RestClient 与 ElasticsearchClient 都由 Spring Boot 自动配置，连接地址、账号、超时
 * 统一写在 spring.elasticsearch.* 下。这里只补两件属性配置表达不了的事：连接池上限，
 * 以及 ES 专用的 JSON 序列化规则。
 *
 * <p>为什么不复用业务 ObjectMapper：spring.jackson.date-format 是 yyyy-MM-dd HH:mm:ss，
 * 而索引映射把 createTime 声明成 strict_date_optional_time，带空格的日期会被 ES 直接拒收；
 * 反过来，业务侧任何 Jackson 配置调整都会静默改变写入 _source 的内容。所以 ES 单独一套。
 *
 * <p>JsonpMapper 是 Boot 留好的扩展点：JacksonJsonpMapperConfiguration 带
 * ConditionalOnMissingBean，容器里存在 JsonpMapper 时 transport 就用它，不需要去覆盖客户端 Bean。
 */
@Configuration
@ConditionalOnProperty(prefix = "dong.elasticsearch", name = "enabled", havingValue = "true")
public class ElasticsearchConfig {

    /**
     * 单节点最大连接数。
     */
    private static final int MAX_CONN_PER_ROUTE = 50;

    /**
     * 全局最大连接数。
     */
    private static final int MAX_CONN_TOTAL = 100;

    /**
     * 连接池上限。Boot 只暴露了超时属性，没有池大小出口，只能走自定义器。
     *
     * <p>必须实现 customize(HttpAsyncClientBuilder) 这个默认方法，不能在
     * customize(RestClientBuilder) 里自己调 setHttpClientConfigCallback：Boot 先注册自己的
     * 回调（凭据、SSL），再依次调用自定义器，自己覆盖一次会把凭据回调整个顶掉，表现为 401。
     */
    @Bean
    public RestClientBuilderCustomizer elasticsearchRestClientBuilderCustomizer() {
        return new RestClientBuilderCustomizer() {

            @Override
            public void customize(RestClientBuilder builder) {
            }

            @Override
            public void customize(HttpAsyncClientBuilder builder) {
                builder.setMaxConnTotal(MAX_CONN_TOTAL);
                builder.setMaxConnPerRoute(MAX_CONN_PER_ROUTE);
            }

        };
    }

    /**
     * ES 专用 JsonpMapper。Boot 默认的 JacksonJsonpMapper 内部是裸 ObjectMapper，
     * 没有 JavaTimeModule，遇到 LocalDateTime 会直接序列化失败。
     */
    @Bean
    public JsonpMapper jsonpMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return new JacksonJsonpMapper(mapper);
    }

}
