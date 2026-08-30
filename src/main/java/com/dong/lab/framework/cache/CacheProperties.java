package com.dong.lab.framework.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 缓存配置项。抖动比例、双删延迟、重建锁等待等都可在此调整。
 */
@ConfigurationProperties(prefix = "lab.cache")
public class CacheProperties {

    private boolean l1Enabled = true;

    private boolean l2Enabled = true;

    private long l1MaxSize = 10_000L;

    private Duration defaultTtl = Duration.ofMinutes(10);

    private Duration nullValueTtl = Duration.ofSeconds(60);

    private double ttlJitterRatio = 0.1;

    private Duration rebuildLease = Duration.ofSeconds(5);

    private Duration rebuildWait = Duration.ofSeconds(1);

    private Duration doubleDeleteDelay = Duration.ofMillis(500);

    private String invalidationChannel = "lab:cache:invalidate";

    public boolean isL1Enabled() {
        return l1Enabled;
    }

    public void setL1Enabled(boolean l1Enabled) {
        this.l1Enabled = l1Enabled;
    }

    public boolean isL2Enabled() {
        return l2Enabled;
    }

    public void setL2Enabled(boolean l2Enabled) {
        this.l2Enabled = l2Enabled;
    }

    public long getL1MaxSize() {
        return l1MaxSize;
    }

    public void setL1MaxSize(long l1MaxSize) {
        this.l1MaxSize = l1MaxSize;
    }

    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    public void setDefaultTtl(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    public Duration getNullValueTtl() {
        return nullValueTtl;
    }

    public void setNullValueTtl(Duration nullValueTtl) {
        this.nullValueTtl = nullValueTtl;
    }

    public double getTtlJitterRatio() {
        return ttlJitterRatio;
    }

    public void setTtlJitterRatio(double ttlJitterRatio) {
        this.ttlJitterRatio = ttlJitterRatio;
    }

    public Duration getRebuildLease() {
        return rebuildLease;
    }

    public void setRebuildLease(Duration rebuildLease) {
        this.rebuildLease = rebuildLease;
    }

    public Duration getRebuildWait() {
        return rebuildWait;
    }

    public void setRebuildWait(Duration rebuildWait) {
        this.rebuildWait = rebuildWait;
    }

    public Duration getDoubleDeleteDelay() {
        return doubleDeleteDelay;
    }

    public void setDoubleDeleteDelay(Duration doubleDeleteDelay) {
        this.doubleDeleteDelay = doubleDeleteDelay;
    }

    public String getInvalidationChannel() {
        return invalidationChannel;
    }

    public void setInvalidationChannel(String invalidationChannel) {
        this.invalidationChannel = invalidationChannel;
    }

}
