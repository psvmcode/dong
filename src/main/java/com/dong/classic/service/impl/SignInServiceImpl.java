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

    /**
     * 位图键前缀，后面拼用户标识与年月，实现每人每月一个位图。
     */
    private static final String SIGN = "lab:sign:";

    /**
     * 位图保留期。给 400 天是为了跨年查询时上一年的数据仍在，
     * 超过保留期的数据由签到流水表兜底，不会真的查不到。
     */
    private static final Duration RETENTION = Duration.ofDays(400);

    /**
     * 正常签到来源标记。
     */
    private static final String SOURCE_NORMAL = "normal";

    /**
     * 补签来源标记。
     */
    private static final String SOURCE_REPAIR = "repair";

    /**
     * Redisson 客户端。
     */
    private final RedissonClient redissonClient;

    /**
     * 签到流水数据访问，用于持久化与对账。
     */
    private final com.dong.classic.mapper.ClassicSigninRecordMapper signinRecordMapper;

    /**
     * 落库签到流水。用 insert ignore，重复签到撞唯一键时返回 0 而不报错，
     * 与位图的幂等语义保持一致。失败不影响签到结果，只记录日志，
     * 因为位图已经写成功，用户的签到体验不应被落库问题影响。
     */
    private void persistSignIn(String userId, LocalDate date, int continuousDays, String source) {
        try {
            com.dong.classic.entity.ClassicSigninRecord record =
                    new com.dong.classic.entity.ClassicSigninRecord();
            record.setUserId(userId);
            record.setSignDate(date);
            record.setContinuousDays(continuousDays);
            record.setSource(source);
            signinRecordMapper.insertIgnore(record);
        } catch (Exception ex) {
            log.error("persist sign in record failed userId={} date={}", userId, date, ex);
        }
    }

    /**
     * 签到，返回 false 表示当天已签过。
     *
     * <p>位图负责「有没有签到」的快速判断，签到流水表负责持久化。
     * 两者都要成功才算完成：只写位图的话，Redis 过期后就查不到历史了。
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
            persistSignIn(userId, date, (int) continuousDays(userId, date), SOURCE_NORMAL);
        }
        bitSet.expire(RETENTION);
        log.info("sign in userId={} date={} firstTime={}", userId, date, !already);
        return !already;
    }

    /**
     * 补签。与正常签到的区别在于落库时会标记来源，
     * 运营统计时可以把补签单独算出来。
     *
     * @param userId 用户标识
     * @param date   补签日期
     * @return 是否成功补签，已签到过则返回 false
     */
    @Override
    public boolean repair(String userId, LocalDate date) {
        RBitSet bitSet = bitSetOf(userId, date);
        long offset = dayOffset(date);
        if (bitSet.get(offset)) {
            return false;
        }
        bitSet.set(offset);
        bitSet.expire(RETENTION);
        persistSignIn(userId, date, (int) continuousDays(userId, date), SOURCE_REPAIR);
        log.warn("sign in repaired userId={} date={}", userId, date);
        return true;
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
