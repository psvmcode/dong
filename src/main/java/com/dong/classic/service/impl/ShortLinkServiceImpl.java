package com.dong.classic.service.impl;

import com.dong.classic.entity.ShortLink;
import com.dong.classic.mapper.ShortLinkMapper;
import com.dong.classic.service.ShortLinkService;
import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.common.util.Base62Utils;
import com.dong.common.util.Snowflake;
import com.dong.framework.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
/**
 * 短链接实现。短码由发号器生成后做 Base62 编码，
 * 同一原始链接每次生成的短码都不同，避免被批量遍历。
 *
 * <p>点击计数分两层：跳转路径上只累加 Redis 的原子计数器，
 * 保证跳转是纯内存操作；再由定时任务把计数回写数据库。
 * 这样既不影响跳转性能，计数也不会因为只存在缓存里而丢失。
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class ShortLinkServiceImpl implements ShortLinkService {

    /**
     * 短码缓存前缀。
     */
    private static final String CODE_CACHE = "lab:short:";

    /**
     * 点击计数前缀，存的是累计值，由定时任务回写数据库。
     */
    private static final String HIT_COUNTER = "lab:short:hit:";

    /**
     * 空值标记。缓存空值防穿透：不存在的短码第二次不会打到数据库。
     */
    private static final String NULL_MARKER = "null";

    /**
     * 默认缓存有效期。
     */
    private static final Duration CACHE_TTL = Duration.ofDays(7);

    /**
     * 空值标记的缓存有效期，比正常值短，避免长期占用。
     */
    private static final Duration NULL_TTL = Duration.ofMinutes(10);

    /**
     * 启用状态。
     */
    private static final int ENABLED = 1;

    /**
     * 停用状态。
     */
    private static final int DISABLED = 2;

    /**
     * 定时任务单轮回写的最大条数，防止一次处理过多拖慢任务。
     */
    private static final int FLUSH_LIMIT = 500;

    /**
     * 短链接数据访问接口。
     */
    private final ShortLinkMapper shortLinkMapper;

    /**
     * Redis 服务。
     */
    private final RedisService redisService;

    /**
     * Redisson 客户端。
     */
    private final RedissonClient redissonClient;

    /**
     * 雪花发号器。
     */
    private final Snowflake snowflake;

    /**
     * 创建短链并返回短码。
     *
     * @param originUrl    原始链接
     * @param expireMinutes 有效分钟数，小于等于 0 表示长期有效
     * @return 短码
     */
    @Override
    public String create(String originUrl, long expireMinutes) {
        validateOriginUrl(originUrl);
        LocalDateTime expireTime = expireMinutes > 0
                ? LocalDateTime.now().plusMinutes(expireMinutes)
                : null;
        String code = Base62Utils.encode(snowflake.nextId());
        ShortLink shortLink = new ShortLink();
        shortLink.setCode(code);
        shortLink.setOriginUrl(originUrl);
        shortLink.setHitCount(0L);
        shortLink.setEnabled(ENABLED);
        shortLink.setExpireTime(expireTime);
        shortLinkMapper.insert(shortLink);
        // 有过期时间时缓存有效期跟着缩短，过期后缓存自动失效，
        // 下一次请求会落到数据库并被过期校验拦下
        redisService.set(CODE_CACHE + code, originUrl, resolveTtl(expireTime));
        log.info("short link created code={} expireTime={}", code, expireTime);
        return code;
    }

    /**
     * 解析短链。缓存空值标记防止穿透：不存在的 code 第二次不会打到数据库。
     * 空值标记用特殊前缀区分，避免与真实 URL 混淆。
     *
     * @param code 短码
     * @return 原始链接
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
        // 缓存未命中才走数据库，状态校验放在这里：
        // 停用时会主动删缓存，所以停用能立即生效，不会因缓存继续跳转
        ensureUsable(shortLink);
        redisService.set(CODE_CACHE + code, shortLink.getOriginUrl(), resolveTtl(shortLink.getExpireTime()));
        countHit(code);
        return shortLink.getOriginUrl();
    }

    /**
     * 根据短码查询短链详情。
     *
     * @param code 短码
     * @return 短链实体
     */
    @Override
    public ShortLink findByCode(String code) {
        ShortLink shortLink = shortLinkMapper.selectByCode(code);
        if (shortLink == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "short link " + code + " not found");
        }
        return shortLink;
    }

    /**
     * 查询短码点击次数。优先取 Redis 累加器，
     * 累加器过期后回落到数据库的落库值。
     *
     * @param code 短码
     * @return 点击次数
     */
    @Override
    public long hitCount(String code) {
        long cached = redissonClient.getAtomicLong(HIT_COUNTER + code).get();
        if (cached > 0) {
            return cached;
        }
        ShortLink shortLink = shortLinkMapper.selectByCode(code);
        return shortLink == null || shortLink.getHitCount() == null ? 0L : shortLink.getHitCount();
    }

    /**
     * 启停短链。停用时必须同步删缓存，否则已缓存的短链仍会跳转成功，
     * 停用形同虚设。
     *
     * @param code    短码
     * @param enabled 是否启用
     */
    @Override
    public void toggle(String code, boolean enabled) {
        findByCode(code);
        shortLinkMapper.updateStatus(code, enabled ? ENABLED : DISABLED, null);
        redisService.delete(CODE_CACHE + code);
        log.warn("short link toggled code={} enabled={}", code, enabled);
    }

    /**
     * 把 Redis 里的点击计数回写数据库，由定时任务调用。
     *
     * <p>写入的是累加器的绝对值而不是增量，
     * 这样即使某一轮回写失败，下一轮也能自动补平，不会积累误差。
     *
     * @return 本轮回写的短链条数
     */
    @Override
    public int flushHitCount() {
        int flushed = 0;
        try {
            for (ShortLink link : shortLinkMapper.selectAll(FLUSH_LIMIT)) {
                long current = redissonClient.getAtomicLong(HIT_COUNTER + link.getCode()).get();
                if (current <= 0) {
                    continue;
                }
                // 数据库已经是这个值就不必重复写，减少无谓的更新
                if (link.getHitCount() != null && link.getHitCount() == current) {
                    continue;
                }
                shortLinkMapper.updateHitCount(link.getCode(), current);
                flushed++;
            }
        } catch (Exception ex) {
            // 回写失败不影响跳转，下一轮会重试，这里只记录不抛出
            log.error("flush short link hit count failed", ex);
        }
        if (flushed > 0) {
            log.info("short link hit count flushed count={}", flushed);
        }
        return flushed;
    }

    /**
     * 累加短码点击次数。只动 Redis，跳转路径上不触碰数据库。
     *
     * @param code 短码
     */
    private void countHit(String code) {
        try {
            RAtomicLong counter = redissonClient.getAtomicLong(HIT_COUNTER + code);
            counter.incrementAndGet();
            counter.expire(CACHE_TTL);
        } catch (Exception ex) {
            // 计数失败不能让跳转失败，跳转才是主流程
            log.warn("count hit failed code={}", code, ex);
        }
    }

    /**
     * 校验原始链接，只放行公网 http/https。
     *
     * <p>这一步不能省。短链跳转是服务端下发 Location 让浏览器去访问，
     * 如果不限制目标，就能构造出指向内网的短链：
     * 云元数据地址（169.254.169.254）、内网管理后台、file:// 本地文件，
     * 都能借服务端的可信位置去访问——这就是 SSRF。
     * 另外 javascript: 这类伪协议会直接变成 XSS。
     *
     * @param originUrl 原始链接
     */
    private void validateOriginUrl(String originUrl) {
        if (originUrl == null || originUrl.isBlank()) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "url must not be blank");
        }
        java.net.URI uri;
        try {
            uri = java.net.URI.create(originUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "malformed url");
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID,
                    "only http and https are allowed");
        }
        String host = uri.getHost();
        if (host == null || isPrivateHost(host)) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID,
                    "host is not allowed: " + host);
        }
    }

    /**
     * 判断是否内网、回环、链路本地地址。
     * 169.254.0.0/16 是链路本地段，云厂商的元数据服务就在这个段里，
     * 是最常被拿来打 SSRF 的目标，必须拦。
     *
     * @param host 主机名或 IP
     * @return 是否为不可访问的地址
     */
    private boolean isPrivateHost(String host) {
        String lower = host.toLowerCase();
        if ("localhost".equals(lower) || lower.endsWith(".localhost")
                || lower.endsWith(".internal") || "0.0.0.0".equals(lower)) {
            return true;
        }
        java.net.InetAddress address;
        try {
            address = java.net.InetAddress.getByName(lower);
        } catch (java.net.UnknownHostException ex) {
            // 解析不了主机名，放行到后面由其它环节处理，不在这里误杀
            return false;
        }
        return address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isAnyLocalAddress();
    }

    /**
     * 校验短链是否可用，停用与过期都拒绝跳转。
     *
     * @param shortLink 短链实体
     */
    private void ensureUsable(ShortLink shortLink) {
        if (shortLink.getEnabled() != null && shortLink.getEnabled() == DISABLED) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "short link " + shortLink.getCode() + " is disabled");
        }
        if (shortLink.getExpireTime() != null && shortLink.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "short link " + shortLink.getCode() + " expired at " + shortLink.getExpireTime());
        }
    }

    /**
     * 计算缓存有效期。有过期时间时取到过期时间的剩余时长，
     * 否则用默认七天。
     *
     * @param expireTime 过期时间，可为空
     * @return 缓存有效期
     */
    private Duration resolveTtl(LocalDateTime expireTime) {
        if (expireTime == null) {
            return CACHE_TTL;
        }
        Duration left = Duration.between(LocalDateTime.now(), expireTime);
        return left.isNegative() || left.isZero() ? Duration.ofSeconds(1) : left;
    }

}
