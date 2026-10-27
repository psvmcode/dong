package com.dong.rpc.registry.impl;

import com.dong.rpc.registry.ServiceDiscovery;
import com.dong.rpc.registry.ServiceInstance;
import com.dong.rpc.registry.ServiceRegistryKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 Redis ZSet 的服务发现。
 *
 * <p>服务列表在本地缓存几秒：注册中心的一次往返往往比调用本身还贵，
 * 每次调用都查一次，等于把注册中心的延迟加进每一次业务调用——
 * 实测从云服务器上的 Redis 取一次列表要 30 毫秒，而调用本身不到 1 毫秒。
 * 代价是「实例已下线」最多延迟一个缓存周期才被感知，
 * 这个取舍成立的前提是调用失败要能容忍：连不上就换一个实例，而不是直接放弃。
 *
 * <p><b>Redis 不可用时要降级到配置的地址，而不是直接报「服务不存在」。</b>
 * 这条是本项目一以贯之的原则：中间件故障不应该被放大成业务不可用。
 * 单机部署时注册地址本来就是本机，降级后的地址和注册中心里的一模一样，
 * 于是「注册中心挂了」只是一次带 warn 日志的正常调用。
 *
 * <p>降级地址可配，是为了多机部署时也能留一条兜底路径——
 * 兜底地址指向最稳定的那台，比「直接失败」有用得多。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dong.rpc", name = "enabled", havingValue = "true")
public class RedisServiceDiscovery implements ServiceDiscovery {

    /**
     * 发现失败告警的退避时长，毫秒。
     */
    private static final long WARN_BACKOFF_MS = 10_000L;

    /**
     * stringRedisTemplate，Redis 字符串模板。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 实例心跳的有效期，毫秒。
     */
    @Value("${dong.rpc.instance-ttl-ms:30000}")
    private long instanceTtlMs;

    /**
     * 注册中心不可用时的兜底地址，多个用逗号分隔。
     */
    @Value("${dong.rpc.default-addresses:127.0.0.1:9090}")
    private String defaultAddresses;

    /**
     * 服务列表的本地缓存时长，毫秒。
     */
    @Value("${dong.rpc.discovery-cache-ms:5000}")
    private long cacheMillis;

    /**
     * 下一次允许告警的时间。
     */
    private final AtomicLong nextWarnAt = new AtomicLong();

    /**
     * 服务列表的本地缓存。
     */
    private final ConcurrentMap<String, Cached> cache = new ConcurrentHashMap<>();

    /**
     * 缓存命中次数。
     */
    private final AtomicLong cacheHits = new AtomicLong();

    /**
     * 缓存未命中次数。
     */
    private final AtomicLong cacheMisses = new AtomicLong();

    @Override
    public List<ServiceInstance> discover(String serviceName) {
        long now = System.currentTimeMillis();
        Cached cached = cache.get(serviceName);
        if (cached != null && cached.expireAt() > now) {
            cacheHits.incrementAndGet();
            return cached.instances();
        }
        cacheMisses.incrementAndGet();
        List<ServiceInstance> instances = lookup(serviceName);
        // 空结果不缓存：服务还没起来时把「没有提供者」缓存住，
        // 会让刚上线的服务在缓存过期前一直不可用，这是最难排查的一类故障
        if (instances.isEmpty()) {
            cache.remove(serviceName);
            return instances;
        }
        cache.put(serviceName, new Cached(instances, now + cacheMillis));
        return instances;
    }

    /**
     * 缓存命中次数。
     *
     * @return 命中次数
     */
    public long cacheHits() {
        return cacheHits.get();
    }

    /**
     * 缓存未命中次数。
     *
     * @return 未命中次数
     */
    public long cacheMisses() {
        return cacheMisses.get();
    }

    /**
     * 真正查注册中心。
     *
     * @param serviceName 服务名
     * @return 实例列表
     */
    private List<ServiceInstance> lookup(String serviceName) {
        try {
            Set<String> members = stringRedisTemplate.opsForZSet().rangeByScore(ServiceRegistryKeys.of(serviceName), System.currentTimeMillis() - instanceTtlMs, Long.MAX_VALUE);
            if (members == null || members.isEmpty()) {
                return List.of();
            }
            List<ServiceInstance> instances = new ArrayList<>(members.size());
            for (String member : members) {
                instances.add(ServiceInstance.parse(serviceName, member));
            }
            return instances;
        } catch (Exception ex) {
            warnOnce(ex);
            return fallback(serviceName);
        }
    }

    /**
     * 降级到配置的兜底地址。
     *
     * @param serviceName 服务名
     * @return 实例列表
     */
    private List<ServiceInstance> fallback(String serviceName) {
        List<ServiceInstance> instances = new ArrayList<>();
        for (String address : defaultAddresses.split(",")) {
            String trimmed = address.trim();
            if (!trimmed.isEmpty()) {
                instances.add(ServiceInstance.parse(serviceName, trimmed));
            }
        }
        return instances;
    }

    /**
     * 按退避节奏报一次失败。
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
            log.warn("rpc discovery fell back to {} because redis is unavailable: {}", defaultAddresses, ex.getMessage());
        }
    }

    /**
     * 缓存的一份服务列表。
     *
     * @param instances 实例列表
     * @param expireAt  过期时间戳，毫秒
     */
    private record Cached(List<ServiceInstance> instances, long expireAt) {
    }

}
