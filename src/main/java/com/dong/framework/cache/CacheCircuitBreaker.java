package com.dong.framework.cache;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 缓存依赖的熔断器。状态存在进程内，每个节点各自判定，
 * 这正好符合它的定位：保护的是本节点到下游的这条链路。
 *
 * <p>为什么需要它：缓存依赖（Redis）或回源依赖（数据库）出故障时，
 * 每一次访问都要等到超时才失败。线程和连接被这些注定失败的等待占满，
 * 故障就从「慢」放大成「整个服务不可用」。熔断的作用是快速失败，
 * 把等待换成一次判断，给下游留出恢复时间。
 *
 * <p>三态转换：
 * <ul>
 *     <li>CLOSED：正常放行，连续失败达到阈值转 OPEN</li>
 *     <li>OPEN：直接拒绝，不再消耗下游资源，到期后转 HALF_OPEN</li>
 *     <li>HALF_OPEN：放一个探测请求，成功回 CLOSED，失败立刻回 OPEN</li>
 * </ul>
 */
public class CacheCircuitBreaker {

    /**
     * 被保护的依赖名称，只用于日志与排查。
     */
    private final String name;

    /**
     * 连续失败多少次后打开熔断。
     */
    private final long failureThreshold;

    /**
     * 熔断打开后保持多久。
     */
    private final long openDurationMillis;

    private final AtomicLong consecutiveFailures = new AtomicLong();

    // 0 表示未打开，大于 0 表示熔断截止时间戳
    private final AtomicLong openUntilMillis = new AtomicLong();

    // 半开态只允许一个探测请求在飞，否则恢复瞬间又被全部流量打回
    private final AtomicBoolean probeInFlight = new AtomicBoolean();

    private final LongAdder openedTimes = new LongAdder();

    /**
     * 构造熔断器。
     *
     * @param name               依赖名称
     * @param failureThreshold   连续失败阈值
     * @param openDurationMillis 熔断保持时长（毫秒）
     */
    public CacheCircuitBreaker(String name, long failureThreshold, long openDurationMillis) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.openDurationMillis = openDurationMillis;
    }

    /**
     * 判断是否允许发起请求。
     *
     * @return 允许返回 true
     */
    public boolean allowRequest() {
        long until = openUntilMillis.get();
        long now = System.currentTimeMillis();
        if (until == 0) {
            return true;
        }
        if (now < until) {
            return false;
        }
        return probeInFlight.compareAndSet(false, true);
    }

    /**
     * 记录一次成功，回到关闭态并清空失败计数。
     */
    public void recordSuccess() {
        consecutiveFailures.set(0L);
        openUntilMillis.set(0L);
        probeInFlight.set(false);
    }

    /**
     * 记录一次失败。达到阈值或半开探测失败时打开熔断。
     */
    public void recordFailure() {
        long count = consecutiveFailures.incrementAndGet();
        if (count >= failureThreshold || openUntilMillis.get() > 0) {
            openUntilMillis.set(System.currentTimeMillis() + openDurationMillis);
            probeInFlight.set(false);
            openedTimes.increment();
        }
    }

    /**
     * 返回当前状态名称。
     *
     * @return CLOSED、OPEN 或 HALF_OPEN
     */
    public String state() {
        long until = openUntilMillis.get();
        if (until == 0) {
            return "CLOSED";
        }
        return System.currentTimeMillis() < until ? "OPEN" : "HALF_OPEN";
    }

    /**
     * 返回熔断打开次数。
     *
     * @return 打开次数
     */
    public long openedTimes() {
        return openedTimes.sum();
    }

    /**
     * 返回当前连续失败次数。
     *
     * @return 连续失败次数
     */
    public long failureCount() {
        return consecutiveFailures.get();
    }

    /**
     * 返回被保护的依赖名称。
     *
     * @return 依赖名称
     */
    public String name() {
        return name;
    }

}
