package com.dong.lab.classic.service.impl;

import com.dong.lab.classic.service.UniqueVisitorService;
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

    private static final String UV = "lab:uv:";

    private static final Duration RETENTION = Duration.ofDays(90);

    /**
     * redissonClient。
     */
    private final RedissonClient redissonClient;

    @Override
    /**
     * record。
     */
    public long record(String page, String visitorId, LocalDate date) {
        RHyperLogLog<String> counter = counter(page, date);
        counter.add(visitorId);
        counter.expire(RETENTION);
        return counter.count();
    }

    @Override
    /**
     * count。
     */
    public long count(String page, LocalDate date) {
        return counter(page, date).count();
    }

    @Override
    /**
     * countBetween。
     */
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
     * counter。
     */
    private RHyperLogLog<String> counter(String page, LocalDate date) {
        return redissonClient.getHyperLogLog(keyOf(page, date));
    }

    /**
     * keyOf。
     */
    private String keyOf(String page, LocalDate date) {
        return UV + page + ":" + date;
    }

}
