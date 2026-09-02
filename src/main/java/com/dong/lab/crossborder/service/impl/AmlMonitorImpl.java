package com.dong.lab.crossborder.service.impl;

import com.dong.lab.crossborder.service.AmlMonitor;
import com.dong.lab.framework.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 拆分交易检测实现。全部状态放 Redis，窗口按自然日滚动。
 *
 * <p>判定规则与申报制度对齐：单笔及累计达到申报线（10000）必须报告，
 * 因此嫌疑特征是「累计达到申报线、且每笔都在线下、笔数达到拆分门槛」。
 * 单笔超线的交易本身会正常申报，不算拆分。
 *
 * <p>计数用 Lua 原子完成，并发汇款时不会漏计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AmlMonitorImpl implements AmlMonitor {

    /**
     * 大额交易申报线。中美两国都是 10000 单位，这里统一按该值模拟。
     */
    static final BigDecimal REPORTING_LINE = new BigDecimal("10000");

    /**
     * 贴线笔数达到该值即构成拆分嫌疑。
     */
    static final int STRUCTURING_COUNT_THRESHOLD = 3;

    private static final String KEY_PREFIX = "lab:crossborder:aml:";

    /**
     * 累计金额、笔数与贴线笔数一次更新完毕。
     * 金额与申报线都以分参与运算，单位不一致会让贴线判断永远失效。
     * 低于申报线的才算贴线，单笔超线会正常申报，不构成拆分。
     * 返回贴线笔数，供调用方判断是否命中。
     */
    private static final String RECORD_SCRIPT = """
            local amountCents = tonumber(ARGV[1])
            local lineCents = tonumber(ARGV[2])
            local underLine = 0
            if amountCents < lineCents then
                underLine = 1
            end
            redis.call('HINCRBY', KEYS[1], 'totalCents', math.floor(amountCents))
            redis.call('HINCRBY', KEYS[1], 'count', 1)
            local underCount = redis.call('HINCRBY', KEYS[1], 'underCount', underLine)
            if underLine == 1 then
                redis.call('SADD', KEYS[1] .. ':under', ARGV[1])
            end
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
            redis.call('EXPIRE', KEYS[1] .. ':under', tonumber(ARGV[3]))
            return underCount
            """;

    private static final RedisScript<Long> RECORD = new DefaultRedisScript<>(RECORD_SCRIPT, Long.class);

    private static final Duration WINDOW_TTL = Duration.ofHours(48);

    private final RedisService redisService;

    @Override
    public Optional<String> detectStructuring(Long payerAccountId, BigDecimal amount) {
        String key = keyOf(payerAccountId);
        long cents = amount.multiply(new BigDecimal("100")).longValue();
        long lineCents = REPORTING_LINE.multiply(new BigDecimal("100")).longValue();
        Long underCount = redisService.execute(RECORD, List.of(key),
                cents, lineCents, WINDOW_TTL.toSeconds());
        if (underCount == null) {
            return Optional.empty();
        }
        if (underCount >= STRUCTURING_COUNT_THRESHOLD) {
            String detail = "structuring suspected: " + underCount + " payments just under the reporting line "
                    + REPORTING_LINE + " within one day, totalCents="
                    + redisService.hashGetAll(key).getOrDefault("totalCents", "0");
            log.warn("aml alert accountId={} {}", payerAccountId, detail);
            return Optional.of(detail);
        }
        return Optional.empty();
    }

    @Override
    public Map<String, Object> structuringProfile(Long payerAccountId) {
        String key = keyOf(payerAccountId);
        Map<String, String> entries = redisService.hashGetAll(key);
        Set<String> under = redisService.template().opsForSet().members(key + ":under");
        long totalCents = Long.parseLong(entries.getOrDefault("totalCents", "0"));
        return Map.of(
                "payerAccountId", payerAccountId,
                "totalCount", Long.parseLong(entries.getOrDefault("count", "0")),
                "totalAmount", BigDecimal.valueOf(totalCents, 2),
                "underLineCount", Long.parseLong(entries.getOrDefault("underCount", "0")),
                "underLineAmounts", under == null ? List.of() : new ArrayList<>(under),
                "date", LocalDate.now().toString());
    }

    @Override
    public List<Map<String, Object>> flaggedAccounts() {
        List<Map<String, Object>> flagged = new ArrayList<>();
        Set<String> keys = redisService.template().keys(KEY_PREFIX + "*");
        if (keys == null) {
            return flagged;
        }
        for (String key : keys.stream().sorted().toList()) {
            if (key.endsWith(":under")) {
                continue;
            }
            Map<String, String> entries = redisService.hashGetAll(key);
            if (Long.parseLong(entries.getOrDefault("underCount", "0")) >= STRUCTURING_COUNT_THRESHOLD) {
                String accountId = key.substring(key.lastIndexOf(':') + 1);
                flagged.add(Map.of(
                        "payerAccountId", Long.parseLong(accountId),
                        "underCount", entries.getOrDefault("underCount", "0"),
                        "totalCents", entries.getOrDefault("totalCents", "0")));
            }
        }
        return flagged;
    }

    @Override
    public void reset() {
        Set<String> keys = redisService.template().keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisService.template().delete(keys);
        }
    }

    private String keyOf(Long payerAccountId) {
        return KEY_PREFIX + LocalDate.now() + ":" + payerAccountId;
    }

}
