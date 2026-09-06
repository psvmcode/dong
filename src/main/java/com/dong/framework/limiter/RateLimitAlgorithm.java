package com.dong.framework.limiter;

/**
 * 限流算法。四种算法在同一突发流量下的表现差异明显：
 * 固定窗口在边界处最多放过两倍配额；
 * 滑动窗口精确但需要维护时间戳，占内存；
 * 令牌桶允许突发；
 * 漏桶强制匀速，会削平突发。
 *
 * <p>注意并非所有实现都支持全部算法，调用前需要看 supportedAlgorithms。
 */
public enum RateLimitAlgorithm {

    FIXED_WINDOW,

    SLIDING_WINDOW,

    TOKEN_BUCKET,

    LEAKY_BUCKET
}
