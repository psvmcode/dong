package com.dong.classic.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 限流算法对比请求。
 *
 * <p>只打一轮突发时四种算法的放行数量必然相同，因为窗口内都最多放行 limit 个，
 * 区分不出算法。真正的差异在配额如何恢复，要看第二轮，
 * 所以要靠 gapMillis 在两轮突发之间留出等待时间。
 */
public class LimiterCompareQueryRequest {

    /**
     * 业务键。
     */
    @NotBlank
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String bizKey = "demo";

    /**
     * 窗口内配额。
     */
    @Min(1)
    @Max(1_000_000)
    private long limit = 10;

    /**
     * 窗口长度，单位秒。建议调短一些，例如 6 秒，便于观察差异。
     */
    @Min(1)
    @Max(Constants.MAX_WINDOW_SECONDS)
    private long windowSeconds = 60;

    /**
     * 每轮突发的尝试次数。
     */
    @Min(1)
    @Max(Constants.MAX_BATCH_SIZE)
    private int attempts = 50;

    /**
     * 是否走分布式实现。
     */
    private boolean distributed = true;

    /**
     * 两轮突发之间的间隔，单位毫秒。设为 0 则四种算法表现一致，看不出差别。
     */
    @Min(0)
    @Max(60_000)
    private long gapMillis = 0;

    /**
     * 获取业务键。
     *
     * @return 业务键
     */
    public String getBizKey() {
        return bizKey;
    }

    /**
     * 设置业务键。
     *
     * @param bizKey 业务键
     */
    public void setBizKey(String bizKey) {
        this.bizKey = bizKey;
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
     * 获取每轮突发的尝试次数。
     *
     * @return 尝试次数
     */
    public int getAttempts() {
        return attempts;
    }

    /**
     * 设置每轮突发的尝试次数。
     *
     * @param attempts 尝试次数
     */
    public void setAttempts(int attempts) {
        this.attempts = attempts;
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

    /**
     * 获取两轮突发之间的间隔。
     *
     * @return 间隔，单位毫秒
     */
    public long getGapMillis() {
        return gapMillis;
    }

    /**
     * 设置两轮突发之间的间隔。
     *
     * @param gapMillis 间隔，单位毫秒
     */
    public void setGapMillis(long gapMillis) {
        this.gapMillis = gapMillis;
    }

}
