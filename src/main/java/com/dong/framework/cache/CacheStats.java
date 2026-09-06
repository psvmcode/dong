package com.dong.framework.cache;

import java.util.concurrent.atomic.LongAdder;

/**
 * 缓存命中统计。用 LongAdder 而不是 AtomicLong，
 * 因为命中统计是高频写、低频读的场景，LongAdder 的分段累加能显著降低竞争。
 */
public class CacheStats {

    private final LongAdder l1Hit = new LongAdder();

    private final LongAdder l2Hit = new LongAdder();

    private final LongAdder miss = new LongAdder();

    // 被空值标记或布隆过滤器挡掉的请求数，用来量化防穿透的效果
    private final LongAdder penetrationBlocked = new LongAdder();

    // 真正回源查数据库的次数，这个值高说明缓存没起作用
    private final LongAdder rebuild = new LongAdder();

    /**
     * 记录 L1 命中。
     */
    public void recordL1Hit() {
        l1Hit.increment();
    }

    /**
     * 记录 L2 命中。
     */
    public void recordL2Hit() {
        l2Hit.increment();
    }

    /**
     * 记录缓存未命中。
     */
    public void recordMiss() {
        miss.increment();
    }

    /**
     * 记录被穿透防护拦截的请求。
     */
    public void recordPenetrationBlocked() {
        penetrationBlocked.increment();
    }

    /**
     * 记录回源重建。
     */
    public void recordRebuild() {
        rebuild.increment();
    }

    /**
     * 重置所有统计计数。
     */
    public void reset() {
        l1Hit.reset();
        l2Hit.reset();
        miss.reset();
        penetrationBlocked.reset();
        rebuild.reset();
    }

    /**
     * 命中率只按 L1、L2、未命中三者计算，
     * 被拦截的穿透请求不计入分母，否则防护措施做得越好命中率反而越难看。
     */
    public CacheStatsSnapshot snapshot() {
        long total = l1Hit.sum() + l2Hit.sum() + miss.sum();
        return new CacheStatsSnapshot(l1Hit.sum(), l2Hit.sum(), miss.sum(),
                penetrationBlocked.sum(), rebuild.sum(),
                total == 0 ? 0.0 : (l1Hit.sum() + l2Hit.sum()) * 100.0 / total);
    }

    /**
     * 缓存统计快照记录。
     *
     * @param l1Hit           L1 命中数
     * @param l2Hit           L2 命中数
     * @param miss            未命中数
     * @param penetrationBlocked 穿透拦截数
     * @param rebuild         回源重建数
     * @param hitRatioPercent 命中率百分比
     */
    public record CacheStatsSnapshot(long l1Hit, long l2Hit, long miss,
                                     long penetrationBlocked, long rebuild, double hitRatioPercent) {
    }

}
