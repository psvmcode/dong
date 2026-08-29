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

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl implements ShortLinkService {

    private static final String CODE_CACHE = "lab:short:";

    private static final String HIT_COUNTER = "lab:short:hit:";

    private static final Duration CACHE_TTL = Duration.ofDays(7);

    private final ShortLinkMapper shortLinkMapper;

    private final RedisService redisService;

    private final RedissonClient redissonClient;

    private final Snowflake snowflake;

    @Override
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

    @Override
    public String resolve(String code) {
        String cached = redisService.get(CODE_CACHE + code).orElse(null);
        if (cached != null) {
            countHit(code);
            return cached;
        }
        ShortLink shortLink = findByCode(code);
        redisService.set(CODE_CACHE + code, shortLink.getOriginUrl(), CACHE_TTL);
        countHit(code);
        return shortLink.getOriginUrl();
    }

    @Override
    public ShortLink findByCode(String code) {
        ShortLink shortLink = shortLinkMapper.selectByCode(code);
        if (shortLink == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "short link " + code + " not found");
        }
        return shortLink;
    }

    @Override
    public long hitCount(String code) {
        return redissonClient.getAtomicLong(HIT_COUNTER + code).get();
    }

    private void countHit(String code) {
        RAtomicLong counter = redissonClient.getAtomicLong(HIT_COUNTER + code);
        counter.incrementAndGet();
        counter.expire(Duration.ofDays(7));
    }

}
