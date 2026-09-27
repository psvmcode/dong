package com.dong.framework.cache;

import java.util.concurrent.atomic.LongAdder;

/**
 * 缓存命中统计。用 LongAdder 而不是 AtomicLong，
 * 因为命中统计是高频写、低频读的场景，LongAdder 的分段累加能显著降低竞争。
 *
 * <p>除了命中率，这里还刻意统计降级相关计数：
 * 命中率只反映「缓存有没有帮上忙」，看不出「缓存挂了之后系统表现如何」。
 * staleServed 和 degraded 才是判断降级策略是否真的生效的依据。
 */
public class CacheStats {

    private final LongAdder l1Hit = new LongAdder();

    private final LongAdder l2Hit = new LongAdder();

    private final LongAdder miss = new LongAdder();

    // 被空值标记或布隆过滤器挡掉的请求数，用来量化防穿透的效果
    private final LongAdder penetrationBlocked = new LongAdder();

    // 真正回源查数据库的次数，这个值高说明缓存没起作用
    private final LongAdder rebuild = new LongAdder();

    // 回源失败后用旧值兜底的次数，说明降级在起作用
    private final LongAdder staleServed = new LongAdder();

    // 回源失败且无旧值可用的次数，这些请求只能明确报错
    private final LongAdder degraded = new LongAdder();

    // 回源被频控拦下的次数，说明已经到了数据库能承受的上限
    private final LongAdder rebuildRejected = new LongAdder();

    // 没抢到重建锁而直接放弃的次数，属于正常让位，不是故障
    private final LongAdder rebuildSkipped = new LongAdder();

    // 熔断器打开期间被拦下的回源次数
    private final LongAdder circuitBlocked = new LongAdder();

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
     * 记录用过期旧值兜底的次数。
     */
    public void recordStaleServed() {
        staleServed.increment();
    }

    /**
     * 记录无旧值可用、只能失败的次数。
     */
    public void recordDegraded() {
        degraded.increment();
    }

    /**
     * 记录回源被频控拒绝的次数。
     */
    public void recordRebuildRejected() {
        rebuildRejected.increment();
    }

    /**
     * 记录因未抢到重建锁而放弃回源的次数。
     */
    public void recordRebuildSkipped() {
        rebuildSkipped.increment();
    }

    /**
     * 记录被熔断器拦下的回源次数。
     */
    public void recordCircuitBlocked() {
        circuitBlocked.increment();
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
        staleServed.reset();
        degraded.reset();
        rebuildRejected.reset();
        rebuildSkipped.reset();
        circuitBlocked.reset();
    }

    /**
     * 命中率只按 L1、L2、未命中三者计算，
     * 被拦截的穿透请求不计入分母，否则防护措施做得越好命中率反而越难看。
     */
    public CacheStatsSnapshot snapshot() {
        long hits = l1Hit.sum() + l2Hit.sum();
        long total = hits + miss.sum();
        return new CacheStatsSnapshot(l1Hit.sum(), l2Hit.sum(), miss.sum(), penetrationBlocked.sum(), rebuild.sum(),
                staleServed.sum(), degraded.sum(), rebuildRejected.sum(), rebuildSkipped.sum(), circuitBlocked.sum(),
                total == 0 ? 0.0 : hits * 100.0 / total);
    }

    /**
     * 缓存统计快照记录。
     *
     * @param l1Hit              L1 命中数
     * @param l2Hit              L2 命中数
     * @param miss               未命中数
     * @param penetrationBlocked 穿透拦截数
     * @param rebuild            回源重建数
     * @param staleServed        旧值兜底数
     * @param degraded           无值可降级数
     * @param rebuildRejected    回源被频控拒绝数
     * @param rebuildSkipped     回源让位数
     * @param circuitBlocked     熔断拦截数
     * @param hitRatioPercent    命中率百分比
     */
    public record CacheStatsSnapshot(long l1Hit, long l2Hit, long miss, long penetrationBlocked, long rebuild,
                                     long staleServed, long degraded, long rebuildRejected, long rebuildSkipped,
                                     long circuitBlocked, double hitRatioPercent) {
    }

}
