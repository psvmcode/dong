package com.dong.framework.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 缓存配置项。抖动比例、双删延迟、重建锁等待等都可在此调整。
 */
@ConfigurationProperties(prefix = "dong.cache")
public class CacheProperties {

    /**
     * 是否启用 L1 本地缓存。
     */
    private boolean l1Enabled = true;

    /**
     * 是否启用 L2 Redis 缓存。
     */
    private boolean l2Enabled = true;

    /**
     * L1 缓存最大条目数。
     */
    private long l1MaxSize = 10_000L;

    /**
     * 默认 TTL。
     */
    private Duration defaultTtl = Duration.ofMinutes(10);

    /**
     * 空值标记 TTL。
     */
    private Duration nullValueTtl = Duration.ofSeconds(60);

    /**
     * TTL 抖动比例，避免集中过期。
     */
    private double ttlJitterRatio = 0.1;

    /**
     * 缓存重建锁持有时间。
     */
    private Duration rebuildLease = Duration.ofSeconds(5);

    /**
     * 缓存重建等待时间。
     */
    private Duration rebuildWait = Duration.ofSeconds(1);

    /**
     * 双删延迟时间。
     */
    private Duration doubleDeleteDelay = Duration.ofMillis(500);

    /**
     * 缓存失效广播频道。
     */
    private String invalidationChannel = "lab:cache:invalidate";

    /**
     * 逻辑过期后仍保留旧值的宽容期，这段时间内旧值可作降级兜底。
     */
    private Duration staleGrace = Duration.ofMinutes(5);

    /**
     * 是否启用回源熔断。关掉则每次都尝试回源，故障时会把线程耗在超时上。
     */
    private boolean breakerEnabled = true;

    /**
     * 连续回源失败多少次后打开熔断。
     */
    private int breakerFailureThreshold = 5;

    /**
     * 熔断打开后保持多久，到期转半开放一个探测请求。
     */
    private Duration breakerOpenDuration = Duration.ofSeconds(10);

    /**
     * 全局每秒最多允许多少次回源，是 Redis 整体不可用时保护数据库的闸门。
     */
    private long rebuildGlobalLimitPerSecond = 200L;

    /**
     * 单个 key 每秒最多允许多少次回源，防击穿防护失效时靠它兜底。
     */
    private long rebuildKeyLimitPerSecond = 20L;

    /**
     * 是否启用 L1 本地缓存。
     *
     * @return 是否启用
     */
    public boolean isL1Enabled() {
        return l1Enabled;
    }

    /**
     * 设置是否启用 L1 本地缓存。
     *
     * @param l1Enabled 是否启用
     */
    public void setL1Enabled(boolean l1Enabled) {
        this.l1Enabled = l1Enabled;
    }

    /**
     * 是否启用 L2 Redis 缓存。
     *
     * @return 是否启用
     */
    public boolean isL2Enabled() {
        return l2Enabled;
    }

    /**
     * 设置是否启用 L2 Redis 缓存。
     *
     * @param l2Enabled 是否启用
     */
    public void setL2Enabled(boolean l2Enabled) {
        this.l2Enabled = l2Enabled;
    }

    /**
     * 获取 L1 缓存最大条目数。
     *
     * @return 最大条目数
     */
    public long getL1MaxSize() {
        return l1MaxSize;
    }

    /**
     * 设置 L1 缓存最大条目数。
     *
     * @param l1MaxSize 最大条目数
     */
    public void setL1MaxSize(long l1MaxSize) {
        this.l1MaxSize = l1MaxSize;
    }

    /**
     * 获取默认 TTL。
     *
     * @return 默认 TTL
     */
    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    /**
     * 设置默认 TTL。
     *
     * @param defaultTtl 默认 TTL
     */
    public void setDefaultTtl(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    /**
     * 获取空值标记 TTL。
     *
     * @return 空值标记 TTL
     */
    public Duration getNullValueTtl() {
        return nullValueTtl;
    }

    /**
     * 设置空值标记 TTL。
     *
     * @param nullValueTtl 空值标记 TTL
     */
    public void setNullValueTtl(Duration nullValueTtl) {
        this.nullValueTtl = nullValueTtl;
    }

    /**
     * 获取 TTL 抖动比例。
     *
     * @return 抖动比例
     */
    public double getTtlJitterRatio() {
        return ttlJitterRatio;
    }

    /**
     * 设置 TTL 抖动比例。
     *
     * @param ttlJitterRatio 抖动比例
     */
    public void setTtlJitterRatio(double ttlJitterRatio) {
        this.ttlJitterRatio = ttlJitterRatio;
    }

    /**
     * 获取缓存重建锁持有时间。
     *
     * @return 重建锁持有时间
     */
    public Duration getRebuildLease() {
        return rebuildLease;
    }

    /**
     * 设置缓存重建锁持有时间。
     *
     * @param rebuildLease 重建锁持有时间
     */
    public void setRebuildLease(Duration rebuildLease) {
        this.rebuildLease = rebuildLease;
    }

    /**
     * 获取缓存重建等待时间。
     *
     * @return 重建等待时间
     */
    public Duration getRebuildWait() {
        return rebuildWait;
    }

    /**
     * 设置缓存重建等待时间。
     *
     * @param rebuildWait 重建等待时间
     */
    public void setRebuildWait(Duration rebuildWait) {
        this.rebuildWait = rebuildWait;
    }

    /**
     * 获取双删延迟时间。
     *
     * @return 双删延迟时间
     */
    public Duration getDoubleDeleteDelay() {
        return doubleDeleteDelay;
    }

    /**
     * 设置双删延迟时间。
     *
     * @param doubleDeleteDelay 双删延迟时间
     */
    public void setDoubleDeleteDelay(Duration doubleDeleteDelay) {
        this.doubleDeleteDelay = doubleDeleteDelay;
    }

    /**
     * 获取缓存失效广播频道。
     *
     * @return 频道名称
     */
    public String getInvalidationChannel() {
        return invalidationChannel;
    }

    /**
     * 设置缓存失效广播频道。
     *
     * @param invalidationChannel 频道名称
     */
    public void setInvalidationChannel(String invalidationChannel) {
        this.invalidationChannel = invalidationChannel;
    }

    /**
     * 获取逻辑过期后保留旧值的宽容期。
     *
     * @return 宽容期
     */
    public Duration getStaleGrace() {
        return staleGrace;
    }

    /**
     * 设置逻辑过期后保留旧值的宽容期。
     *
     * @param staleGrace 宽容期
     */
    public void setStaleGrace(Duration staleGrace) {
        this.staleGrace = staleGrace;
    }

    /**
     * 是否启用回源熔断。
     *
     * @return 是否启用
     */
    public boolean isBreakerEnabled() {
        return breakerEnabled;
    }

    /**
     * 设置是否启用回源熔断。
     *
     * @param breakerEnabled 是否启用
     */
    public void setBreakerEnabled(boolean breakerEnabled) {
        this.breakerEnabled = breakerEnabled;
    }

    /**
     * 获取连续回源失败阈值。
     *
     * @return 失败阈值
     */
    public int getBreakerFailureThreshold() {
        return breakerFailureThreshold;
    }

    /**
     * 设置连续回源失败阈值。
     *
     * @param breakerFailureThreshold 失败阈值
     */
    public void setBreakerFailureThreshold(int breakerFailureThreshold) {
        this.breakerFailureThreshold = breakerFailureThreshold;
    }

    /**
     * 获取熔断打开时长。
     *
     * @return 熔断打开时长
     */
    public Duration getBreakerOpenDuration() {
        return breakerOpenDuration;
    }

    /**
     * 设置熔断打开时长。
     *
     * @param breakerOpenDuration 熔断打开时长
     */
    public void setBreakerOpenDuration(Duration breakerOpenDuration) {
        this.breakerOpenDuration = breakerOpenDuration;
    }

    /**
     * 获取全局每秒回源上限。
     *
     * @return 每秒回源上限
     */
    public long getRebuildGlobalLimitPerSecond() {
        return rebuildGlobalLimitPerSecond;
    }

    /**
     * 设置全局每秒回源上限。
     *
     * @param rebuildGlobalLimitPerSecond 每秒回源上限
     */
    public void setRebuildGlobalLimitPerSecond(long rebuildGlobalLimitPerSecond) {
        this.rebuildGlobalLimitPerSecond = rebuildGlobalLimitPerSecond;
    }

    /**
     * 获取单 key 每秒回源上限。
     *
     * @return 每秒回源上限
     */
    public long getRebuildKeyLimitPerSecond() {
        return rebuildKeyLimitPerSecond;
    }

    /**
     * 设置单 key 每秒回源上限。
     *
     * @param rebuildKeyLimitPerSecond 每秒回源上限
     */
    public void setRebuildKeyLimitPerSecond(long rebuildKeyLimitPerSecond) {
        this.rebuildKeyLimitPerSecond = rebuildKeyLimitPerSecond;
    }

}
