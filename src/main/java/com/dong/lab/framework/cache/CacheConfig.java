package com.dong.lab.framework.cache;

import com.dong.lab.config.ExecutorConfig;
import com.dong.lab.framework.redis.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/**
 * 缓存装配。L1 与 L2 都按开关决定是否注册，
 * 因此多级缓存拿到的某一层可能是 null，调用处必须判空。
 */
@Configuration
@EnableConfigurationProperties(CacheProperties.class)
@RequiredArgsConstructor
public class CacheConfig {

    private final ApplicationContext applicationContext;

    @Bean
    public CacheStats cacheStats() {
        return new CacheStats();
    }

    @Bean
    @ConditionalOnProperty(prefix = "lab.cache", name = "l2-enabled", havingValue = "true", matchIfMissing = true)
    public RedisCacheStore redisCacheStore(RedisService redisService, CacheProperties properties) {
        return new RedisCacheStore(redisService, properties.getTtlJitterRatio());
    }

    @Bean
    @ConditionalOnProperty(prefix = "lab.cache", name = "l1-enabled", havingValue = "true", matchIfMissing = true)
    public CaffeineCacheStore caffeineCacheStore(CacheProperties properties) {
        return new CaffeineCacheStore(properties.getL1MaxSize());
    }

    @Bean
    public CacheEventBus cacheEventBus(RedisService redisService, CacheProperties properties) {
        return new CacheEventBus(redisService, properties.getInvalidationChannel());
    }

    @Bean
    public MultiLevelCache multiLevelCache(CacheEventBus eventBus,
                                           com.dong.lab.framework.lock.DistributedLockService distributedLockService,
                                           ExecutorConfig.DelayedTaskRunner delayedTaskRunner,
                                           CacheProperties properties,
                                           CacheStats stats) {
        CaffeineCacheStore l1 = beanIfPresent(CaffeineCacheStore.class);
        RedisCacheStore l2 = beanIfPresent(RedisCacheStore.class);
        return new MultiLevelCache(l1, l2, eventBus, distributedLockService, delayedTaskRunner, properties, stats);
    }

    @Bean
    @ConditionalOnProperty(prefix = "lab.cache", name = "l1-enabled", havingValue = "true", matchIfMissing = true)
    public RedisMessageListenerContainer cacheInvalidationContainer(RedisConnectionFactory connectionFactory,
                                                                    CacheEventBus eventBus) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> {
            byte[] body = message.getBody();
            if (body != null) {
                eventBus.dispatch(new String(body, StandardCharsets.UTF_8));
            }
        }, new PatternTopic(eventBus.getChannel()));
        return container;
    }

    /**
     * 按类型取 bean，不存在返回 null。
     * L1/L2 可独立关闭，这里不能因为某一层缺失就启动失败。
     */
    private <T> T beanIfPresent(Class<T> type) {
        try {
            return applicationContext.getBean(type);
        } catch (org.springframework.beans.factory.NoSuchBeanDefinitionException ex) {
            return null;
        }
    }

}
