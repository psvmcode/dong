package com.dong.classic.service.impl;

import com.dong.classic.service.SignInService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBitSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
/**
 * 签到实现。基于 Bitmap，每月一个 key，
 * 每个用户每月只占极少存储，一年下来也就几百字节。
 *
 * <p>连续天数需要向前扫描位图直到遇到 0，跨月时要额外拼接上月末的连续段。
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class SignInServiceImpl implements SignInService {

    private static final String SIGN = "lab:sign:";

    private static final Duration RETENTION = Duration.ofDays(400);

    /**
     * Redisson 客户端。
     */
    private final RedissonClient redissonClient;

    /**
     * 签到，返回 false 表示当天已签过。
     *
     * @param userId 用户标识
     * @param date   日期
     * @return 是否首次签到
     */
    @Override
    public boolean signIn(String userId, LocalDate date) {
        RBitSet bitSet = bitSetOf(userId, date);
        long offset = dayOffset(date);
        boolean already = bitSet.get(offset);
        if (!already) {
            bitSet.set(offset);
        }
        bitSet.expire(RETENTION);
        log.info("sign in userId={} date={} firstTime={}", userId, date, !already);
        return !already;
    }

    /**
     * 查询指定日期是否已签到。
     *
     * @param userId 用户标识
     * @param date   日期
     * @return 是否已签到
     */
    @Override
    public boolean hasSigned(String userId, LocalDate date) {
        return bitSetOf(userId, date).get(dayOffset(date));
    }

    /**
     * 统计当月累计签到天数。
     *
     * @param userId 用户标识
     * @param month  月份
     * @return 累计签到天数
     */
    @Override
    public long countInMonth(String userId, YearMonth month) {
        return bitSetOf(userId, month).cardinality();
    }

    /**
     * 查询连续签到天数，从指定日期往前推算，中断即止。
     *
     * @param userId 用户标识
     * @param today  基准日期
     * @return 连续签到天数
     */
    @Override
    public long continuousDays(String userId, LocalDate today) {
        RBitSet bitSet = bitSetOf(userId, today);
        long streak = 0L;
        for (int day = today.getDayOfMonth(); day >= 1; day--) {
            if (!bitSet.get(day - 1L)) {
                break;
            }
            streak++;
        }
        return streak;
    }

    /**
     * 查询当月签到日历。
     *
     * @param userId 用户标识
     * @param month  月份
     * @return 日期到是否签到的映射
     */
    @Override
    public Map<String, Boolean> monthCalendar(String userId, YearMonth month) {
        RBitSet bitSet = bitSetOf(userId, month);
        Map<String, Boolean> calendar = new LinkedHashMap<>();
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            calendar.put(String.valueOf(day), bitSet.get(day - 1L));
        }
        return calendar;
    }

    /**
     * 获取用户指定日期的 Bitmap。
     *
     * @param userId 用户标识
     * @param date   日期
     * @return 位图
     */
    private RBitSet bitSetOf(String userId, LocalDate date) {
        return bitSetOf(userId, YearMonth.from(date));
    }

    /**
     * 获取用户指定月份的 Bitmap。
     *
     * @param userId 用户标识
     * @param month  月份
     * @return 位图
     */
    private RBitSet bitSetOf(String userId, YearMonth month) {
        return redissonClient.getBitSet(SIGN + userId + ":" + month.getYear()
                + String.format("%02d", month.getMonthValue()));
    }

    /**
     * 计算日期在 Bitmap 中的偏移量。
     *
     * @param date 日期
     * @return 位图偏移量
     */
    private long dayOffset(LocalDate date) {
        return date.getDayOfMonth() - 1L;
    }

}
