package com.dong.ad.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 广告链路依赖熔断器。Redis 与 MQ 各自独立，一个挂了不影响另一个。
 *
 * <p>没有熔断器时，中间件故障会让每个请求都先等满客户端 5 秒超时再降级，
 * 线程池被这些「注定失败但还在等待」的请求占满，
 * 表现为服务整体不可用，而故障点只在其中一个依赖上。
 *
 * <p>三态：CLOSED 正常放行；连续失败达阈值后 OPEN 直接快速失败；
 * 冷却期结束后 HALF_OPEN 只放一个探针，成功即恢复，失败立刻重新断路。
 */
@Slf4j
@Component
public class AdDependencyBreaker {

    /**
     * 单个依赖的熔断状态。
     */
    private static final class State {

        private final AtomicInteger failures = new AtomicInteger();

        private final AtomicLong openedUntil = new AtomicLong();

        private final AtomicBoolean probing = new AtomicBoolean();

    }

    /**
     * Redis 熔断器状态。
     */
    private final State redis = new State();

    /**
     * MQ 熔断器状态。
     */
    private final State mq = new State();

    /**
     * 连续失败多少次才断路。
     */
    @Value("${dong.ad.breaker-failure-threshold:5}")
    private int failureThreshold;

    /**
     * 断路持续时间，单位毫秒。
     */
    @Value("${dong.ad.breaker-open-duration-ms:10000}")
    private long openDurationMs;

    /**
     * Redis 是否允许调用。
     *
     * @return 是否放行
     */
    public boolean allowRedis() {
        return allow(redis, "redis");
    }

    /**
     * Redis 调用成功，重置熔断状态。
     */
    public void redisSuccess() {
        reset(redis);
    }

    /**
     * Redis 调用失败，累计并按需断路。
     *
     * @param ex 失败原因
     */
    public void redisFailure(Throwable ex) {
        recordFailure(redis, "redis", ex);
    }

    /**
     * MQ 是否允许调用。
     *
     * @return 是否放行
     */
    public boolean allowMq() {
        return allow(mq, "mq");
    }

    /**
     * MQ 调用成功，重置熔断状态。
     */
    public void mqSuccess() {
        reset(mq);
    }

    /**
     * MQ 调用失败，累计并按需断路。
     *
     * @param ex 失败原因
     */
    public void mqFailure(Throwable ex) {
        recordFailure(mq, "mq", ex);
    }

    /**
     * 生成熔断状态快照，供健康检查与运行时诊断使用。
     *
     * @return 状态快照
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("redis", describe(redis));
        snapshot.put("mq", describe(mq));
        return snapshot;
    }

    /**
     * 判定是否放行。冷却期结束后只放一个探针，避免半开瞬间被并发打穿。
     *
     * @param state 熔断状态
     * @param name  依赖名称，仅用于日志
     * @return 是否放行
     */
    private boolean allow(State state, String name) {
        long openedUntil = state.openedUntil.get();
        if (openedUntil <= 0L) {
            return true;
        }
        if (System.currentTimeMillis() < openedUntil) {
            return false;
        }
        if (state.probing.compareAndSet(false, true)) {
            log.info("ad breaker {} half open, one probe allowed", name);
            return true;
        }
        return false;
    }

    /**
     * 成功后清空失败计数、断路标记与探针标记。
     *
     * @param state 熔断状态
     */
    private void reset(State state) {
        state.failures.set(0);
        state.openedUntil.set(0L);
        state.probing.set(false);
    }

    /**
     * 累计失败。半开探针失败时立即重新断路，不等阈值。
     *
     * @param state 熔断状态
     * @param name  依赖名称
     * @param ex    失败原因
     */
    private void recordFailure(State state, String name, Throwable ex) {
        int failures = state.failures.incrementAndGet();
        boolean probing = state.probing.get();
        if (failures >= Math.max(1, failureThreshold) || probing) {
            long until = System.currentTimeMillis() + Math.max(1L, openDurationMs);
            if (state.openedUntil.get() != until) {
                state.openedUntil.set(until);
                log.warn("ad breaker {} opened until={} failures={} probe={} cause={}", name, until, failures, probing,
                        ex == null ? "" : ex.toString());
            }
            // 半开探针失败后必须复位，否则下一轮 CAS 永远拿不到探针名额，熔断器再也无法自愈
            state.probing.set(false);
        }
    }

    /**
     * 描述单个依赖的熔断状态。
     *
     * @param state 熔断状态
     * @return 状态描述
     */
    private Map<String, Object> describe(State state) {
        long openedUntil = state.openedUntil.get();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("failures", state.failures.get());
        info.put("open", openedUntil > System.currentTimeMillis());
        info.put("probing", state.probing.get());
        info.put("openUntil", openedUntil);
        return info;
    }

}
