package com.dong.lab.classic.service;

import java.util.Map;

/**
 * 限流算法对比。四种算法在同一突发流量下的放行数量差异明显：
 * 固定窗口在窗口边界处最多放过两倍配额；
 * 滑动窗口精确但需要维护时间戳列表，占内存；
 * 令牌桶允许突发，只要桶里有令牌就能通过；
 * 漏桶强制匀速，突发流量会被削平。
 */
public interface RateLimitLabService {

    /**
     * 用同一突发流量分别测试四种算法，返回各自的放行与拒绝数量。
     */
    Map<String, Object> compare(String bizKey, long limit, long windowSeconds, int attempts, boolean distributed);

}
