package com.dong.lab.classic.service;

import java.util.Map;

/**
 * 限流算法对比。
 *
 * <p>四种算法的语义差异：
 * 固定窗口按时间轴切窗口，边界处最多放过两倍配额；
 * 滑动窗口记录每次请求的时间戳，任意滑动窗口内都不超额，精确但占内存；
 * 令牌桶按速率补充令牌，允许突发；
 * 漏桶按速率漏水，输出恒定。
 *
 * <p>两种观察维度能看出不同差异：
 *
 * <p>其一，看连续请求的总量。固定窗口与滑动窗口严格不超过 limit，
 * 而令牌桶与漏桶会略超，因为它们限制的是平均速率而非窗口内瞬时总量。
 * 实测 limit=10、窗口 6 秒时，三十次请求打到远程 Redis 约耗时一到两秒，
 * 期间按速率补充了几个额度，令牌桶与漏桶因此放行 11 个。这是正确行为，不是超额。
 *
 * <p>其二，看间隔后的恢复量。四种算法恢复方式不同：
 * 固定窗口只有跨过窗口边界才一次性归零，滑动窗口放行滑出窗口的那部分，
 * 令牌桶按补充速率放行，漏桶按漏水速率放行。
 */
public interface RateLimitLabService {

    /**
     * 只打一轮突发，四种算法的放行数量会相同，仅用于验证配额上限。
     */
    Map<String, Object> compare(String bizKey, long limit, long windowSeconds, int attempts, boolean distributed);

    /**
     * 打两轮突发，中间等待 delayMillis 毫秒。
     * 第二轮能区分算法：固定窗口只有跨窗口才放行，
     * 滑动窗口放行滑出窗口的那部分，令牌桶放行补充的令牌，漏桶放行漏出的水量。
     */
    Map<String, Object> compare(String bizKey, long limit, long windowSeconds, int attempts,
                                boolean distributed, long delayMillis);

}
