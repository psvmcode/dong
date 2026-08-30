package com.dong.lab.classic.service.impl;

import com.dong.lab.classic.service.IdGeneratorService;
import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import com.dong.lab.common.util.Snowflake;
import com.dong.lab.framework.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RIdGenerator;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 发号器实现。四种策略各有取舍：
 * 雪花算法趋势递增但依赖机器时钟，号段模式对数据库有压力但绝对递增，
 * INCR 最简单但会暴露业务量，UUID 无序不适合做数据库主键。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdGeneratorServiceImpl implements IdGeneratorService {

    private static final String SEGMENT_KEY = "lab:id:segment";

    private final Snowflake snowflake;

    private final RedisService redisService;

    private final RedissonClient redissonClient;

    @Override
    public Map<String, Object> generate(String strategy, int count) {
        AtomicLong last = new AtomicLong();
        long start = System.nanoTime();
        switch (strategy.toLowerCase()) {
            case "snowflake" -> {
                for (int i = 0; i < count; i++) {
                    last.set(snowflake.nextId());
                }
            }
            case "segment" -> {
                RIdGenerator generator = redissonClient.getIdGenerator(SEGMENT_KEY);
                generator.tryInit(1L, 1000L);
                for (int i = 0; i < count; i++) {
                    last.set(generator.nextId());
                }
            }
            case "redis" -> {
                for (int i = 0; i < count; i++) {
                    last.set(redisService.increment("lab:id:sequence"));
                }
            }
            case "uuid" -> {
                for (int i = 0; i < count; i++) {
                    last.set(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE);
                }
            }
            default -> throw new BusinessException(Constants.CODE_PARAM_INVALID,
                    "unknown strategy " + strategy + ", use snowflake | segment | redis | uuid");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("strategy", strategy);
        result.put("count", count);
        result.put("lastId", last.get());
        result.put("elapsedMillis", (System.nanoTime() - start) / 1_000_000.0);
        return result;
    }

}
