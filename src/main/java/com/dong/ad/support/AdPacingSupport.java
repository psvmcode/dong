package com.dong.ad.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 预算平滑投放（pacing）。
 *
 * <p>真实的广告预算不是「今天有多少钱就能花多少钱」，
 * 而是按一天的时间节奏逐步释放：早上花光、下午空窗，既浪费预算又拿不到全天的流量。
 * 这里按当天已过分钟数算出「此刻理论上应该累计花到多少」，
 * 花超了就先刹车，而不是等数据库日预算硬挡住才停。
 *
 * <p>pacing 是软闸门，数据库日预算仍是硬闸门：
 * 副本或时钟出偏差时最多少投，不会多扣一分钱。
 */
@Slf4j
@Component
public class AdPacingSupport {

    /**
     * 一天的分钟数。
     */
    private static final double MINUTES_PER_DAY = 1440D;

    /**
     * pacing 开关。
     */
    @Value("${dong.ad.pacing-enabled:true}")
    private boolean enabled;

    /**
     * 放量系数，大于 1 表示允许适度超前。
     */
    @Value("${dong.ad.pacing-factor:1.0}")
    private double factor;

    /**
     * 容忍度，超过理论累计值的这个比例才算超前。
     */
    @Value("${dong.ad.pacing-tolerance:0.2}")
    private double tolerance;

    /**
     * 按当前时刻计算还可以花多少。用于召回过滤与预算闸门初始化。
     *
     * @param dailyBudgetCents 日预算，单位分
     * @param chargedCents     今日已计费，单位分
     * @param now              当前时间
     * @return 当前可花额度，单位分
     */
    public long availableCents(long dailyBudgetCents, long chargedCents, LocalDateTime now) {
        long remaining = Math.max(0L, dailyBudgetCents - chargedCents);
        if (!enabled || dailyBudgetCents <= 0L) {
            return remaining;
        }
        long target = targetCents(dailyBudgetCents, now);
        return Math.min(remaining, Math.max(0L, target - chargedCents));
    }

    /**
     * 是否已经花得比时间节奏快。快太多就先刹车，避免下午无预算可投。
     *
     * @param dailyBudgetCents 日预算，单位分
     * @param chargedCents     今日已计费，单位分
     * @param now              当前时间
     * @return 是否超前
     */
    public boolean aheadOfSchedule(long dailyBudgetCents, long chargedCents, LocalDateTime now) {
        if (!enabled || dailyBudgetCents <= 0L) {
            return false;
        }
        long target = targetCents(dailyBudgetCents, now);
        if (target <= 0L) {
            return false;
        }
        double limit = target * (1D + Math.max(0D, tolerance));
        return chargedCents > limit;
    }

    /**
     * 生成 pacing 状态，便于页面解释「为什么还有预算却没投」。
     *
     * @param dailyBudgetCents 日预算，单位分
     * @param chargedCents     今日已计费，单位分
     * @param now              当前时间
     * @return 状态快照
     */
    public Map<String, Object> snapshot(long dailyBudgetCents, long chargedCents, LocalDateTime now) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("enabled", enabled);
        snapshot.put("factor", factor);
        snapshot.put("tolerance", tolerance);
        snapshot.put("targetByNowCents", targetCents(dailyBudgetCents, now));
        snapshot.put("chargedCents", chargedCents);
        snapshot.put("ahead", aheadOfSchedule(dailyBudgetCents, chargedCents, now));
        return snapshot;
    }

    /**
     * 计算此刻理论上应累计花到的金额。
     *
     * @param dailyBudgetCents 日预算，单位分
     * @param now              当前时间
     * @return 理论累计金额，单位分
     */
    private long targetCents(long dailyBudgetCents, LocalDateTime now) {
        int minute = now.getHour() * 60 + now.getMinute();
        double ratio = Math.min(1D, (minute + 1D) / MINUTES_PER_DAY);
        double adjusted = Math.max(0D, factor) * ratio;
        return Math.min(dailyBudgetCents, (long) (dailyBudgetCents * adjusted));
    }

}
