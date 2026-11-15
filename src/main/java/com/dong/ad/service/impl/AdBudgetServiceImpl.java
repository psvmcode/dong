package com.dong.ad.service.impl;

import com.dong.ad.service.AdBudgetService;
import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.framework.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 预算闸门与频次控制实现。
 *
 * <p>预扣脚本支持惰性初始化：键不存在时直接用「日预算 - 库中今日已花费」建键，
 * 因此不需要像秒杀那样先人工预热，Redis 全量丢失后下一次请求就能自愈。
 *
 * <p>归还脚本只在键存在时回加。键已过期时 INCRBY 会从零起底造出一个
 * 无过期时间的「幽灵配额」，让早已结束的日期重新显示出可投放额度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdBudgetServiceImpl implements AdBudgetService {

    private static final String BUDGET_KEY = "lab:ad:budget:";

    private static final String FREQ_KEY = "lab:ad:freq:";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 返回值约定：非负数表示扣减后剩余配额，-2 表示配额不足。
     */
    private static final String RESERVE_SCRIPT = """
            local left = tonumber(redis.call('get', KEYS[1]))
            if left == nil then
                left = tonumber(ARGV[2])
                redis.call('set', KEYS[1], left, 'ex', ARGV[3])
            end
            local amount = tonumber(ARGV[1])
            if left < amount then
                return -2
            end
            redis.call('decrby', KEYS[1], amount)
            return left - amount
            """;

    private static final RedisScript<Long> RESERVE = new DefaultRedisScript<>(RESERVE_SCRIPT, Long.class);

    private static final String RESTORE_SCRIPT = """
            if redis.call('exists', KEYS[1]) == 1 then
                redis.call('incrby', KEYS[1], tonumber(ARGV[1]))
            end
            return 1
            """;

    private static final RedisScript<Long> RESTORE = new DefaultRedisScript<>(RESTORE_SCRIPT, Long.class);

    private static final String FREQ_INCR_SCRIPT = """
            local current = redis.call('incr', KEYS[1])
            if current == 1 then
                redis.call('expire', KEYS[1], tonumber(ARGV[1]))
            end
            return current
            """;

    private static final RedisScript<Long> FREQ_INCR = new DefaultRedisScript<>(FREQ_INCR_SCRIPT, Long.class);

    /**
     * redisService，Redis 门面。
     */
    private final RedisService redisService;

    /**
     * 原子预扣当日预算配额。
     */
    @Override
    public long reserve(Long campaignId, LocalDate date, long dailyBudgetCents, long spentTodayCents, long amountCents) {
        long initial = Math.max(0L, dailyBudgetCents - spentTodayCents);
        Long result = redisService.execute(RESERVE, List.of(budgetKey(campaignId, date)), amountCents, initial, ttlSeconds(date));
        if (result == null) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "ad budget reserve script returned nothing");
        }
        return result;
    }

    /**
     * 归还预扣配额。
     */
    @Override
    public void restore(Long campaignId, LocalDate date, long amountCents) {
        redisService.execute(RESTORE, List.of(budgetKey(campaignId, date)), amountCents);
    }

    /**
     * 以数据库流水为准重建当日配额副本。
     */
    @Override
    public void rebuild(Long campaignId, LocalDate date, long dailyBudgetCents, long spentTodayCents) {
        long left = Math.max(0L, dailyBudgetCents - spentTodayCents);
        redisService.set(budgetKey(campaignId, date), String.valueOf(left), Duration.ofSeconds(ttlSeconds(date)));
    }

    /**
     * 读取用户当日曝光次数。写脏的计数按 0 处理，不能因为一个脏值让整条召回失败。
     */
    @Override
    public long frequencyCount(Long campaignId, Long userId, LocalDate date) {
        String value = redisService.get(freqKey(campaignId, userId, date)).orElse("0");
        try {
            return Math.max(0L, Long.parseLong(value));
        } catch (NumberFormatException ex) {
            log.warn("invalid ad frequency value campaign={} user={} value={}", campaignId, userId, value);
            return 0L;
        }
    }

    /**
     * 有效曝光后递增频次计数。
     */
    @Override
    public void incrementFrequency(Long campaignId, Long userId, LocalDate date) {
        Long result = redisService.execute(FREQ_INCR, List.of(freqKey(campaignId, userId, date)), ttlSeconds(date));
        if (result == null) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "ad frequency script returned nothing");
        }
    }

    /**
     * 构造预算键，按归属日分键，日切不需要任何迁移。
     *
     * @param campaignId 计划 id
     * @param date       归属日期
     * @return Redis 键
     */
    private String budgetKey(Long campaignId, LocalDate date) {
        return BUDGET_KEY + date.format(DAY) + Constants.SEPARATOR + campaignId;
    }

    /**
     * 构造频次键，作用域是「某天某计划某用户」。
     *
     * @param campaignId 计划 id
     * @param userId     用户 id
     * @param date       归属日期
     * @return Redis 键
     */
    private String freqKey(Long campaignId, Long userId, LocalDate date) {
        return FREQ_KEY + date.format(DAY) + Constants.SEPARATOR + campaignId + Constants.SEPARATOR + userId;
    }

    /**
     * 计算到次日零点的秒数。历史日期或时钟异常时给一个很短的兜底 TTL，
     * 避免把过期日期的配额永久留在 Redis 里。
     *
     * @param date 归属日期
     * @return TTL 秒数，至少 1
     */
    private long ttlSeconds(LocalDate date) {
        long seconds = Duration.between(LocalDateTime.now(), date.plusDays(1).atStartOfDay()).getSeconds();
        return Math.max(1L, seconds);
    }

}
