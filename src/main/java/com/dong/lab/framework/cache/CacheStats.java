package com.dong.lab.framework.cache;

import java.util.concurrent.atomic.LongAdder;

public class CacheStats {

    private final LongAdder l1Hit = new LongAdder();

    private final LongAdder l2Hit = new LongAdder();

    private final LongAdder miss = new LongAdder();

    private final LongAdder penetrationBlocked = new LongAdder();

    private final LongAdder rebuild = new LongAdder();

    public void recordL1Hit() {
        l1Hit.increment();
    }

    public void recordL2Hit() {
        l2Hit.increment();
    }

    public void recordMiss() {
        miss.increment();
    }

    public void recordPenetrationBlocked() {
        penetrationBlocked.increment();
    }

    public void recordRebuild() {
        rebuild.increment();
    }

    public void reset() {
        l1Hit.reset();
        l2Hit.reset();
        miss.reset();
        penetrationBlocked.reset();
        rebuild.reset();
    }

    public CacheStatsSnapshot snapshot() {
        long total = l1Hit.sum() + l2Hit.sum() + miss.sum();
        return new CacheStatsSnapshot(l1Hit.sum(), l2Hit.sum(), miss.sum(),
                penetrationBlocked.sum(), rebuild.sum(),
                total == 0 ? 0.0 : (l1Hit.sum() + l2Hit.sum()) * 100.0 / total);
    }

    public record CacheStatsSnapshot(long l1Hit, long l2Hit, long miss,
                                     long penetrationBlocked, long rebuild, double hitRatioPercent) {
    }

}
