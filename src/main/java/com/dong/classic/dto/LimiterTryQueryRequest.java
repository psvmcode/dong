package com.dong.classic.dto;

import com.dong.common.constant.Constants;
import com.dong.framework.limiter.RateLimitAlgorithm;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 限流单次尝试请求。algorithm 取 RateLimitAlgorithm 的枚举字面量。
 */
public class LimiterTryQueryRequest {

    /**
     * 限流键。
     */
    @NotBlank
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String key = "demo";

    /**
     * 限流算法。并非每种实现都支持全部算法，不支持时会直接报错而不是静默放行。
     */
    private RateLimitAlgorithm algorithm = RateLimitAlgorithm.TOKEN_BUCKET;

    /**
     * 窗口内配额。
     */
    @Min(1)
    @Max(1_000_000)
    private long limit = 10;

    /**
     * 窗口长度，单位秒。
     */
    @Min(1)
    @Max(Constants.MAX_WINDOW_SECONDS)
    private long windowSeconds = 60;

    /**
     * 是否走分布式实现。多实例部署必须为 true，
     * 否则每个节点各自计数，实际放放量是配额乘以节点数。
     */
    private boolean distributed = true;

    /**
     * 获取限流键。
     *
     * @return 限流键
     */
    public String getKey() {
        return key;
    }

    /**
     * 设置限流键。
     *
     * @param key 限流键
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * 获取限流算法。
     *
     * @return 限流算法
     */
    public RateLimitAlgorithm getAlgorithm() {
        return algorithm;
    }

    /**
     * 设置限流算法。
     *
     * @param algorithm 限流算法
     */
    public void setAlgorithm(RateLimitAlgorithm algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * 获取窗口内配额。
     *
     * @return 配额
     */
    public long getLimit() {
        return limit;
    }

    /**
     * 设置窗口内配额。
     *
     * @param limit 配额
     */
    public void setLimit(long limit) {
        this.limit = limit;
    }

    /**
     * 获取窗口长度。
     *
     * @return 窗口长度，单位秒
     */
    public long getWindowSeconds() {
        return windowSeconds;
    }

    /**
     * 设置窗口长度。
     *
     * @param windowSeconds 窗口长度，单位秒
     */
    public void setWindowSeconds(long windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    /**
     * 是否走分布式实现。
     *
     * @return 是否分布式
     */
    public boolean isDistributed() {
        return distributed;
    }

    /**
     * 设置是否走分布式实现。
     *
     * @param distributed 是否分布式
     */
    public void setDistributed(boolean distributed) {
        this.distributed = distributed;
    }

}
