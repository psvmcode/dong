package com.dong.lab.framework.limiter.impl;

import com.dong.lab.framework.redis.RedisService;
import com.dong.lab.framework.limiter.RateLimitAlgorithm;
import com.dong.lab.framework.limiter.RateLimitRule;
import com.dong.lab.framework.limiter.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
/**
 * 分布式限流器，四种算法全部用 Lua 脚本在 Redis 上实现，计数全局共享。
 *
 * <p>为什么不用 Redisson 的 RRateLimiter：它底层只有令牌桶一种实现，
 * 只能选择全局或按客户端计数，无法表达滑动窗口与漏桶，
 * 导致四种算法退化成同一种行为，对比实验失去意义。这里改为自行实现。
 *
 * <p>四种算法各自的语义：
 * 固定窗口按时间轴切分窗口，窗口切换时计数归零，因此在边界处最多放过两倍配额；
 * 滑动窗口用有序集合记录每次请求的时间戳，任意滑动窗口内都不超过配额，精确但占内存；
 * 令牌桶按速率补充令牌，桶里有存货就允许突发；
 * 漏桶按速率漏水，桶满则拒绝，输出速率恒定。
 *
 * <p>令牌桶与漏桶的配额在持续请求期间会按速率恢复，
 * 因此一个窗口内实际放行的总量可能略高于 limit，
 * 这是它们限制平均速率而非瞬时总量的必然结果，不是超额。
 *
 * <p>时间一律取自 Redis 服务器而不是应用本地时钟。
 * 多实例部署时各节点时钟必然有偏差，用本地时间会让窗口边界不一致，限流结果失真。
 */
@Slf4j
@Component
@RequiredArgsConstructor

public class LuaRateLimiter implements RateLimiter {

    /**
     * 固定窗口。窗口按时间轴对齐，切换时计数归零。
     */
    private static final String FIXED_WINDOW_SCRIPT = """
            local limit = tonumber(ARGV[1])
            local windowMs = tonumber(ARGV[2])
            local permits = tonumber(ARGV[3])
            local t = redis.call('TIME')
            local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
            local idx = math.floor(now / windowMs)
            local data = redis.call('HMGET', KEYS[1], 'idx', 'count')
            local curIdx = tonumber(data[1])
            local count = tonumber(data[2])
            if curIdx == nil or curIdx ~= idx then
                curIdx = idx
                count = 0
            end
            if count + permits <= limit then
                count = count + permits
                redis.call('HSET', KEYS[1], 'idx', curIdx, 'count', count)
                redis.call('PEXPIRE', KEYS[1], windowMs * 2)
                return 1
            end
            redis.call('HSET', KEYS[1], 'idx', curIdx, 'count', count)
            redis.call('PEXPIRE', KEYS[1], windowMs * 2)
            return 0
            """;

    /**
     * 滑动窗口。用有序集合记录时间戳，先清理窗口外的记录再统计。
     * 需要第二个键做序列号，因为同一毫秒内的多次请求不能用相同的成员名。
     */
    private static final String SLIDING_WINDOW_SCRIPT = """
            local limit = tonumber(ARGV[1])
            local windowMs = tonumber(ARGV[2])
            local permits = tonumber(ARGV[3])
            local t = redis.call('TIME')
            local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - windowMs)
            local used = redis.call('ZCARD', KEYS[1])
            if used + permits <= limit then
                local seq = redis.call('INCR', KEYS[2])
                for i = 1, permits do
                    redis.call('ZADD', KEYS[1], now, tostring(now) .. '-' .. tostring(seq) .. '-' .. tostring(i))
                end
                redis.call('PEXPIRE', KEYS[1], windowMs)
                redis.call('PEXPIRE', KEYS[2], windowMs)
                return 1
            end
            redis.call('PEXPIRE', KEYS[1], windowMs)
            return 0
            """;

    /**
     * 令牌桶。按时间比例补充令牌，上限为桶容量，桶内有存货就允许突发。
     */
    private static final String TOKEN_BUCKET_SCRIPT = """
            local capacity = tonumber(ARGV[1])
            local windowMs = tonumber(ARGV[2])
            local permits = tonumber(ARGV[3])
            local t = redis.call('TIME')
            local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
            local data = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
            local tokens = tonumber(data[1])
            local ts = tonumber(data[2])
            if tokens == nil then
                tokens = capacity
                ts = now
            end
            local elapsed = now - ts
            if elapsed > 0 then
                tokens = math.min(capacity, tokens + elapsed * (capacity / windowMs))
                ts = now
            end
            local allowed = 0
            if tokens >= permits then
                tokens = tokens - permits
                allowed = 1
            end
            redis.call('HSET', KEYS[1], 'tokens', string.format('%.6f', tokens), 'ts', ts)
            redis.call('PEXPIRE', KEYS[1], windowMs * 2)
            return allowed
            """;

    /**
     * 漏桶。按固定速率漏水，桶内水量代表待处理请求，满了就拒绝，
     * 因此无论来的多猛，处理速率都是恒定的。
     */
    private static final String LEAKY_BUCKET_SCRIPT = """
            local capacity = tonumber(ARGV[1])
            local windowMs = tonumber(ARGV[2])
            local permits = tonumber(ARGV[3])
            local t = redis.call('TIME')
            local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
            local data = redis.call('HMGET', KEYS[1], 'water', 'ts')
            local water = tonumber(data[1])
            local ts = tonumber(data[2])
            if water == nil then
                water = 0
                ts = now
            end
            local elapsed = now - ts
            if elapsed > 0 then
                water = math.max(0, water - elapsed * (capacity / windowMs))
                ts = now
            end
            local allowed = 0
            if water + permits <= capacity then
                water = water + permits
                allowed = 1
            end
            redis.call('HSET', KEYS[1], 'water', string.format('%.6f', water), 'ts', ts)
            redis.call('PEXPIRE', KEYS[1], windowMs * 2)
            return allowed
            """;

    private static final String KEY_PREFIX = "lab:limiter:lua:";

    private static final RedisScript<Long> FIXED_WINDOW = new DefaultRedisScript<>(FIXED_WINDOW_SCRIPT, Long.class);

    private static final RedisScript<Long> SLIDING_WINDOW = new DefaultRedisScript<>(SLIDING_WINDOW_SCRIPT, Long.class);

    private static final RedisScript<Long> TOKEN_BUCKET = new DefaultRedisScript<>(TOKEN_BUCKET_SCRIPT, Long.class);

    private static final RedisScript<Long> LEAKY_BUCKET = new DefaultRedisScript<>(LEAKY_BUCKET_SCRIPT, Long.class);

    /**
     * Redis 服务。
     */
    private final RedisService redisService;

    /**
     * 尝试获取配额，使用 Redis Lua 脚本实现四种算法。
     *
     * @param key     业务键
     * @param rule    限流规则
     * @param permits 请求配额
     * @return 是否放行
     */
    @Override
    public boolean tryAcquire(String key, RateLimitRule rule, long permits) {
        long windowMs = Math.max(1L, rule.window().toMillis());
        String base = KEY_PREFIX + rule.algorithm().name().toLowerCase() + ":" + key
                + ":" + rule.limit() + ":" + windowMs;
        switch (rule.algorithm()) {
            case FIXED_WINDOW -> {
                return allowed(redisService.execute(FIXED_WINDOW, List.of(base),
                        rule.limit(), windowMs, permits), rule);
            }
            case SLIDING_WINDOW -> {
                return allowed(redisService.execute(SLIDING_WINDOW, List.of(base, base + ":seq"),
                        rule.limit(), windowMs, permits), rule);
            }
            case TOKEN_BUCKET -> {
                return allowed(redisService.execute(TOKEN_BUCKET, List.of(base),
                        rule.limit(), windowMs, permits), rule);
            }
            case LEAKY_BUCKET -> {
                return allowed(redisService.execute(LEAKY_BUCKET, List.of(base),
                        rule.limit(), windowMs, permits), rule);
            }
            default -> throw new IllegalArgumentException("unsupported algorithm " + rule.algorithm());
        }
    }

    /**
     * 返回分布式 Lua 实现支持的所有算法。
     *
     * @return 支持的算法集合
     */
    @Override
    public Set<RateLimitAlgorithm> supportedAlgorithms() {
        return Set.of(RateLimitAlgorithm.values());
    }

    /**
     * 返回限流器名称。
     *
     * @return 名称
     */
    @Override
    public String name() {
        return "lua";
    }

    /**
     * 脚本没有返回结果时按拒绝处理。限流组件不适合抛异常把业务请求打断，
     * 但也不能放行，因此采取保护下游的策略，同时留下告警日志便于发现 Redis 异常。
     */
    private boolean allowed(Long result, RateLimitRule rule) {
        if (result == null) {
            log.warn("rate limit script returned nothing for algorithm {}, rejecting the request",
                    rule.algorithm());
            return false;
        }
        return result > 0L;
    }

}
