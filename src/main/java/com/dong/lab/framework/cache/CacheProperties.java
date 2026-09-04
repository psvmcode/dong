package com.dong.lab.framework.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
/**
 * 缓存配置项。抖动比例、双删延迟、重建锁等待等都可在此调整。
 */
@ConfigurationProperties(prefix = "lab.cache")

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

}
