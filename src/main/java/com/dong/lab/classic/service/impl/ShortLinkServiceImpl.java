package com.dong.lab.classic.service.impl;

import com.dong.lab.classic.entity.ShortLink;
import com.dong.lab.classic.mapper.ShortLinkMapper;
import com.dong.lab.classic.service.ShortLinkService;
import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import com.dong.lab.common.util.Base62Utils;
import com.dong.lab.common.util.Snowflake;
import com.dong.lab.framework.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
/**
 * 短链接实现。短码由发号器生成后做 Base62 编码，
 * 同一原始链接每次生成的短码都不同，避免被批量遍历。
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class ShortLinkServiceImpl implements ShortLinkService {

    private static final String CODE_CACHE = "lab:short:";

    private static final String HIT_COUNTER = "lab:short:hit:";

    private static final String NULL_MARKER = "null";

    private static final Duration CACHE_TTL = Duration.ofDays(7);

    private static final Duration NULL_TTL = Duration.ofMinutes(10);

    /**
     * shortLinkMapper，MyBatis Mapper 数据访问层。
     */
    private final ShortLinkMapper shortLinkMapper;

    /**
     * redisService，业务服务层。
     */
    private final RedisService redisService;

    /**
     * redissonClient。
     */
    private final RedissonClient redissonClient;

    /**
     * snowflake。
     */
    private final Snowflake snowflake;

    @Override
    /**
     * 创建记录。
     */
    public String create(String originUrl) {
        String code = Base62Utils.encode(snowflake.nextId());
        ShortLink shortLink = new ShortLink();
        shortLink.setCode(code);
        shortLink.setOriginUrl(originUrl);
        shortLink.setHitCount(0L);
        shortLinkMapper.insert(shortLink);
        redisService.set(CODE_CACHE + code, originUrl, CACHE_TTL);
        log.info("short link created code={}", code);
        return code;
    }

    /**
     * 解析短链。缓存空值标记防止穿透：不存在的 code 第二次不会打到数据库。
     * 空值标记用特殊前缀区分，避免与真实 URL 混淆。
     */
    @Override
    public String resolve(String code) {
        String cached = redisService.get(CODE_CACHE + code).orElse(null);
        if (cached != null) {
            if (NULL_MARKER.equals(cached)) {
                throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "short link " + code + " not found");
            }
            countHit(code);
            return cached;
        }
        ShortLink shortLink = shortLinkMapper.selectByCode(code);
        if (shortLink == null) {
            redisService.set(CODE_CACHE + code, NULL_MARKER, NULL_TTL);
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "short link " + code + " not found");
        }
        redisService.set(CODE_CACHE + code, shortLink.getOriginUrl(), CACHE_TTL);
        countHit(code);
        return shortLink.getOriginUrl();
    }

    @Override
    /**
     * findByCode。
     */
    public ShortLink findByCode(String code) {
        ShortLink shortLink = shortLinkMapper.selectByCode(code);
        if (shortLink == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "short link " + code + " not found");
        }
        return shortLink;
    }

    @Override
    /**
     * hitCount。
     */
    public long hitCount(String code) {
        return redissonClient.getAtomicLong(HIT_COUNTER + code).get();
    }

    /**
     * countHit。
     */
    private void countHit(String code) {
        RAtomicLong counter = redissonClient.getAtomicLong(HIT_COUNTER + code);
        counter.incrementAndGet();
        counter.expire(Duration.ofDays(7));
    }

}
