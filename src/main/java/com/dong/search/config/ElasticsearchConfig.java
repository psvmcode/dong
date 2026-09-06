package com.dong.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.dong.common.util.JsonUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.net.URI;
/**
 * ElasticsearchConfig，配置类。
 */
@Configuration
@ConditionalOnProperty(prefix = "dong.elasticsearch", name = "enabled", havingValue = "true")

public class ElasticsearchConfig {

    /**
     * uris。
     */
    @Value("${spring.elasticsearch.uris:127.0.0.1:9200}")
    private String uris;

    /**
     * username。
     */
    @Value("${spring.elasticsearch.username:}")
    private String username;

    /**
     * password。
     */
    @Value("${spring.elasticsearch.password:}")
    private String password;

    /**
     * restClient。
     */
    @Bean(destroyMethod = "close")
    public RestClient restClient() {
        HttpHost[] hosts = java.util.Arrays.stream(uris.split(","))
                .map(String::trim)
                .map(ElasticsearchConfig::toHttpHost)
                .toArray(HttpHost[]::new);
        RestClientBuilder builder = RestClient.builder(hosts);
        if (!username.isBlank()) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(username, password));
            builder.setHttpClientConfigCallback(config -> config.setDefaultCredentialsProvider(credentialsProvider));
        }
        return builder.build();
    }

    /**
     * elasticsearchClient。
     */
    @Bean(destroyMethod = "")
    public ElasticsearchClient elasticsearchClient(RestClient restClient) {
        RestClientTransport transport =
                new RestClientTransport(restClient, new JacksonJsonpMapper(JsonUtils.mapper()));
        return new ElasticsearchClient(transport);
    }

    /**
     * toHttpHost。
     */
    private static HttpHost toHttpHost(String value) {
        String normalized = value.contains("://") ? value : "http://" + value;
        URI uri = URI.create(normalized);
        return new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
    }

}
