package com.dong.classic.service.impl;

import com.dong.classic.service.UniqueVisitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RHyperLogLog;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
/**
 * 独立访客实现。基于 HyperLogLog，每个页面每天固定占用约 12KB，
 * 代价是结果有约百分之零点八的误差，金额类场景不能使用。
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class UniqueVisitorServiceImpl implements UniqueVisitorService {

    /**
     * 计数器键前缀，后面拼页面标识与日期，实现每页每天一个计数器。
     */
    private static final String UV = "lab:uv:";

    /**
     * 计数器保留期。估算值只在近期有意义，
     * 历史数据已由每日快照表落库，过期不影响查趋势。
     */
    private static final Duration RETENTION = Duration.ofDays(90);

    /**
     * Redisson 客户端。
     */
    private final RedissonClient redissonClient;

    /**
     * 每日独立访客数据访问，用于把估算值落库保存。
     */
    private final com.dong.classic.mapper.ClassicUvDailyMapper uvDailyMapper;

    /**
     * 记录一次访问并返回估算独立访客数。
     *
     * <p>落库的是当天最后一次的估算值而不是累加值：
     * HyperLogLog 给的是去重后的估算结果，累加会导致数字虚高。
     *
     * @param page      页面标识
     * @param visitorId 访客标识
     * @param date      日期
     * @return 估算独立访客数
     */
    @Override
    public long record(String page, String visitorId, LocalDate date) {
        RHyperLogLog<String> counter = counter(page, date);
        counter.add(visitorId);
        counter.expire(RETENTION);
        long count = counter.count();
        persist(page, date, count);
        return count;
    }

    /**
     * 把估算值落库。失败只记录日志：UV 是统计指标，
     * 不能因为落库问题影响访问主流程。
     */
    private void persist(String page, LocalDate date, long count) {
        try {
            uvDailyMapper.upsert(page, date, count);
        } catch (Exception ex) {
            log.error("persist uv failed page={} date={}", page, date, ex);
        }
    }

    /**
     * 查询指定日期的估算独立访客数。
     *
     * @param page 页面标识
     * @param date 日期
     * @return 估算独立访客数
     */
    @Override
    public long count(String page, LocalDate date) {
        return counter(page, date).count();
    }

    /**
     * 查询日期区间的估算独立访客数，合并多个 HyperLogLog 去重。
     *
     * @param page 页面标识
     * @param from 起始日期
     * @param to   结束日期
     * @return 估算独立访客数
     */
    @Override
    public long countBetween(String page, LocalDate from, LocalDate to) {
        List<String> keys = from.datesUntil(to.plusDays(1))
                .map(date -> keyOf(page, date))
                .toList();
        if (keys.isEmpty()) {
            return 0L;
        }
        if (keys.size() == 1) {
            return counter(page, from).count();
        }
        RHyperLogLog<String> merged = redissonClient.getHyperLogLog(UV + page + ":merged:" + from + "_" + to);
        merged.mergeWith(keys.toArray(new String[0]));
        merged.expire(RETENTION);
        return merged.count();
    }

    /**
     * 获取指定日期的 HyperLogLog 计数器。
     *
     * @param page 页面标识
     * @param date 日期
     * @return HyperLogLog 计数器
     */
    private RHyperLogLog<String> counter(String page, LocalDate date) {
        return redissonClient.getHyperLogLog(keyOf(page, date));
    }

    /**
     * 构建 UV 统计键。
     *
     * @param page 页面标识
     * @param date 日期
     * @return 统计键
     */
    private String keyOf(String page, LocalDate date) {
        return UV + page + ":" + date;
    }

}
