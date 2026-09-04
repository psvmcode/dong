package com.dong.lab.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * Redisson 配置类。
 */
@Configuration

public class RedissonConfig {

    /**
     * Redis 地址。
     */
    @Value("${lab.redisson.address:redis://127.0.0.1:6379}")
    private String address;

    /**
     * Redis 数据库索引。
     */
    @Value("${lab.redisson.database:0}")
    private int database;

    /**
     * Redis 密码。
     */
    @Value("${lab.redisson.password:}")
    private String password;

    /**
     * 连接池最大大小。
     */
    @Value("${lab.redisson.connection-pool-size:32}")
    private int connectionPoolSize;

    /**
     * 连接池最小空闲连接数。
     */
    @Value("${lab.redisson.connection-minimum-idle-size:8}")
    private int connectionMinimumIdleSize;

    /**
     * 连接超时时间（毫秒）。
     */
    @Value("${lab.redisson.timeout:5000}")
    private int timeout;

    /**
     * 创建 Redisson 客户端。
     *
     * @return Redisson 客户端
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.setCodec(new org.redisson.codec.JsonJacksonCodec());
        SingleServerConfig singleServer = config.useSingleServer()
                .setAddress(address)
                .setDatabase(database)
                .setConnectionPoolSize(connectionPoolSize)
                .setConnectionMinimumIdleSize(connectionMinimumIdleSize)
                .setTimeout(timeout)
                .setRetryAttempts(3)
                .setRetryInterval(1000);
        if (password != null && !password.isBlank()) {
            singleServer.setPassword(password);
        }
        return Redisson.create(config);
    }

}
