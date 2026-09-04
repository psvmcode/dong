package com.dong.lab.framework.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
/**
 * 缓存配置项。抖动比例、双删延迟、重建锁等待等都可在此调整。
 */
@ConfigurationProperties(prefix = "lab.cache")

public class CacheProperties {

    /**
     * true。
     */
    private boolean l1Enabled = true;

    /**
     * true。
     */
    private boolean l2Enabled = true;

    /**
     * 10_000L。
     */
    private long l1MaxSize = 10_000L;

    private Duration defaultTtl = Duration.ofMinutes(10);

    private Duration nullValueTtl = Duration.ofSeconds(60);

    /**
     * 1。
     */
    private double ttlJitterRatio = 0.1;

    private Duration rebuildLease = Duration.ofSeconds(5);

    private Duration rebuildWait = Duration.ofSeconds(1);

    private Duration doubleDeleteDelay = Duration.ofMillis(500);

    private String invalidationChannel = "lab:cache:invalidate";

    /**
     * isL1Enabled。
     */
    public boolean isL1Enabled() {
        return l1Enabled;
    }

    /**
     * setL1Enabled。
     */
    public void setL1Enabled(boolean l1Enabled) {
        this.l1Enabled = l1Enabled;
    }

    /**
     * isL2Enabled。
     */
    public boolean isL2Enabled() {
        return l2Enabled;
    }

    /**
     * setL2Enabled。
     */
    public void setL2Enabled(boolean l2Enabled) {
        this.l2Enabled = l2Enabled;
    }

    /**
     * getL1MaxSize。
     */
    public long getL1MaxSize() {
        return l1MaxSize;
    }

    /**
     * setL1MaxSize。
     */
    public void setL1MaxSize(long l1MaxSize) {
        this.l1MaxSize = l1MaxSize;
    }

    /**
     * getDefaultTtl。
     */
    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    /**
     * setDefaultTtl。
     */
    public void setDefaultTtl(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    /**
     * getNullValueTtl。
     */
    public Duration getNullValueTtl() {
        return nullValueTtl;
    }

    /**
     * setNullValueTtl。
     */
    public void setNullValueTtl(Duration nullValueTtl) {
        this.nullValueTtl = nullValueTtl;
    }

    /**
     * getTtlJitterRatio。
     */
    public double getTtlJitterRatio() {
        return ttlJitterRatio;
    }

    /**
     * setTtlJitterRatio。
     */
    public void setTtlJitterRatio(double ttlJitterRatio) {
        this.ttlJitterRatio = ttlJitterRatio;
    }

    /**
     * getRebuildLease。
     */
    public Duration getRebuildLease() {
        return rebuildLease;
    }

    /**
     * setRebuildLease。
     */
    public void setRebuildLease(Duration rebuildLease) {
        this.rebuildLease = rebuildLease;
    }

    /**
     * getRebuildWait。
     */
    public Duration getRebuildWait() {
        return rebuildWait;
    }

    /**
     * setRebuildWait。
     */
    public void setRebuildWait(Duration rebuildWait) {
        this.rebuildWait = rebuildWait;
    }

    /**
     * getDoubleDeleteDelay。
     */
    public Duration getDoubleDeleteDelay() {
        return doubleDeleteDelay;
    }

    /**
     * setDoubleDeleteDelay。
     */
    public void setDoubleDeleteDelay(Duration doubleDeleteDelay) {
        this.doubleDeleteDelay = doubleDeleteDelay;
    }

    /**
     * getInvalidationChannel。
     */
    public String getInvalidationChannel() {
        return invalidationChannel;
    }

    /**
     * setInvalidationChannel。
     */
    public void setInvalidationChannel(String invalidationChannel) {
        this.invalidationChannel = invalidationChannel;
    }

}
