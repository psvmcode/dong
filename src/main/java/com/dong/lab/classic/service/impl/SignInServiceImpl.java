package com.dong.lab.classic.service.impl;

import com.dong.lab.classic.service.SignInService;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class SignInServiceImpl implements SignInService {

    private static final String SIGN = "lab:sign:";

    private static final Duration RETENTION = Duration.ofDays(400);

    private final RedissonClient redissonClient;

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

    @Override
    public boolean hasSigned(String userId, LocalDate date) {
        return bitSetOf(userId, date).get(dayOffset(date));
    }

    @Override
    public long countInMonth(String userId, YearMonth month) {
        return bitSetOf(userId, month).cardinality();
    }

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

    @Override
    public Map<String, Boolean> monthCalendar(String userId, YearMonth month) {
        RBitSet bitSet = bitSetOf(userId, month);
        Map<String, Boolean> calendar = new LinkedHashMap<>();
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            calendar.put(String.valueOf(day), bitSet.get(day - 1L));
        }
        return calendar;
    }

    private RBitSet bitSetOf(String userId, LocalDate date) {
        return bitSetOf(userId, YearMonth.from(date));
    }

    private RBitSet bitSetOf(String userId, YearMonth month) {
        return redissonClient.getBitSet(SIGN + userId + ":" + month.getYear()
                + String.format("%02d", month.getMonthValue()));
    }

    private long dayOffset(LocalDate date) {
        return date.getDayOfMonth() - 1L;
    }

}
