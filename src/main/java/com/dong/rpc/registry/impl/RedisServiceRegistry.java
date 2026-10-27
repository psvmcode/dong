package com.dong.rpc.registry.impl;

import com.dong.rpc.registry.ServiceInstance;
import com.dong.rpc.registry.ServiceRegistry;
import com.dong.rpc.registry.ServiceRegistryKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 Redis ZSet 的服务注册。
 *
 * <p>为什么是 ZSet 而不是 Set：Set 的成员没有独立的过期时间，
 * 一个被 kill -9 的实例会永远留在里面；用 String + TTL 又得为每个实例维护一个 key，
 * 发现时还得先 KEYS 再逐个查（KEYS 在生产是禁用的）。
 * ZSet 的 score 天然就是「最后一次心跳时间」：注册即 ZADD，
 * 发现时按 score 范围取「TTL 之内心跳过的」，清理也只是删一个 score 区间。
 *
 * <p>Redis 不可用时不抛异常给调用方，只记日志并返回失败条数：
 * 注册失败意味着「别人暂时看不见我」，把它变成启动失败就是把中间件故障放大成应用不可用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dong.rpc", name = "enabled", havingValue = "true")
public class RedisServiceRegistry implements ServiceRegistry {

    /**
     * 注册失败告警的退避时长，毫秒。
     */
    private static final long WARN_BACKOFF_MS = 10_000L;

    /**
     * stringRedisTemplate，Redis 字符串模板。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 实例心跳的有效期，毫秒。超过这么久没续约就认为实例已下线。
     */
    @Value("${dong.rpc.instance-ttl-ms:30000}")
    private long instanceTtlMs;

    /**
     * 下一次允许告警的时间。
     */
    private final AtomicLong nextWarnAt = new AtomicLong();

    @Override
    public void register(ServiceInstance instance) {
        try {
            stringRedisTemplate.opsForZSet().add(ServiceRegistryKeys.of(instance.getServiceName()), instance.address(), System.currentTimeMillis());
            log.info("rpc service registered: {} -> {}", instance.getServiceName(), instance.address());
        } catch (Exception ex) {
            warnOnce(ex);
        }
    }

    @Override
    public void unregister(ServiceInstance instance) {
        try {
            stringRedisTemplate.opsForZSet().remove(ServiceRegistryKeys.of(instance.getServiceName()), instance.address());
            log.info("rpc service unregistered: {} -> {}", instance.getServiceName(), instance.address());
        } catch (Exception ex) {
            warnOnce(ex);
        }
    }

    @Override
    public int renew(Collection<ServiceInstance> instances) {
        int renewed = 0;
        for (ServiceInstance instance : instances) {
            try {
                String key = ServiceRegistryKeys.of(instance.getServiceName());
                stringRedisTemplate.opsForZSet().add(key, instance.address(), System.currentTimeMillis());
                // 顺手清掉超时的成员：不做这一步，ZSet 里会一直堆着早已下线的实例
                stringRedisTemplate.opsForZSet().removeRangeByScore(key, 0, System.currentTimeMillis() - instanceTtlMs);
                renewed++;
            } catch (Exception ex) {
                warnOnce(ex);
            }
        }
        return renewed;
    }

    /**
     * 按退避节奏报一次失败。心跳每 10 秒跑一次，不限制的话 Redis 挂一小时就是几百条一样的告警。
     *
     * @param ex 异常
     */
    private void warnOnce(Exception ex) {
        long now = System.currentTimeMillis();
        long next = nextWarnAt.get();
        if (now < next) {
            return;
        }
        if (nextWarnAt.compareAndSet(next, now + WARN_BACKOFF_MS)) {
            log.warn("rpc registry is unavailable: {}", ex.getMessage());
        }
    }

}
