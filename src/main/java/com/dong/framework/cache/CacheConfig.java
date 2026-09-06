package com.dong.framework.cache;

import com.dong.config.ExecutorConfig;
import com.dong.framework.redis.RedisService;
import com.dong.framework.cache.impl.CaffeineCacheStore;
import com.dong.framework.cache.impl.RedisCacheStore;
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

    /**
     * Spring 应用上下文。
     */
    private final ApplicationContext applicationContext;

    /**
     * 注册缓存命中统计组件。
     *
     * @return 缓存统计组件
     */
    @Bean
    public CacheStats cacheStats() {
        return new CacheStats();
    }

    /**
     * 注册 L2 Redis 缓存存储，仅当 L2 启用时生效。
     *
     * @param redisService Redis 服务
     * @param properties   缓存配置
     * @return RedisCacheStore
     */
    @Bean
    @ConditionalOnProperty(prefix = "dong.cache", name = "l2-enabled", havingValue = "true", matchIfMissing = true)
    public RedisCacheStore redisCacheStore(RedisService redisService, CacheProperties properties) {
        return new RedisCacheStore(redisService, properties.getTtlJitterRatio());
    }

    /**
     * 注册 L1 Caffeine 缓存存储，仅当 L1 启用时生效。
     *
     * @param properties 缓存配置
     * @return CaffeineCacheStore
     */
    @Bean
    @ConditionalOnProperty(prefix = "dong.cache", name = "l1-enabled", havingValue = "true", matchIfMissing = true)
    public CaffeineCacheStore caffeineCacheStore(CacheProperties properties) {
        return new CaffeineCacheStore(properties.getL1MaxSize());
    }

    /**
     * 注册缓存失效事件总线。
     *
     * @param redisService Redis 服务
     * @param properties   缓存配置
     * @return 缓存事件总线
     */
    @Bean
    public CacheEventBus cacheEventBus(RedisService redisService, CacheProperties properties) {
        return new CacheEventBus(redisService, properties.getInvalidationChannel());
    }

    /**
     * 注册多级缓存。
     *
     * @param eventBus            缓存事件总线
     * @param distributedLockService 分布式锁服务
     * @param delayedTaskRunner   延迟任务执行器
     * @param properties          缓存配置
     * @param stats               缓存统计组件
     * @return 多级缓存
     */
    @Bean
    public MultiLevelCache multiLevelCache(CacheEventBus eventBus,
                                           com.dong.framework.lock.DistributedLockService distributedLockService,
                                           ExecutorConfig.DelayedTaskRunner delayedTaskRunner,
                                           CacheProperties properties,
                                           CacheStats stats) {
        CaffeineCacheStore l1 = beanIfPresent(CaffeineCacheStore.class);
        RedisCacheStore l2 = beanIfPresent(RedisCacheStore.class);
        return new MultiLevelCache(l1, l2, eventBus, distributedLockService, delayedTaskRunner, properties, stats);
    }

    /**
     * 注册 Redis 缓存失效消息监听容器，仅当 L1 启用时生效。
     *
     * @param connectionFactory Redis 连接工厂
     * @param eventBus          缓存事件总线
     * @return 消息监听容器
     */
    @Bean
    @ConditionalOnProperty(prefix = "dong.cache", name = "l1-enabled", havingValue = "true", matchIfMissing = true)
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
