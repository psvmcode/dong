package com.dong.lab.framework.redis;

import com.dong.lab.common.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
/**
 * Redis 门面，统一封装常用操作与脚本执行。
 *
 * <p>关键点在脚本参数：一律转成字符串再传给 Redis。
 * 之前用 Redisson 的 RScript 执行 Lua 时，返回值被错误解码，
 * 脚本执行成功却拿到 null，导致秒杀库存凭空消失，
 * 所以这里改用 StringRedisTemplate 并对 null 结果保持警惕。
 */
@Component
@RequiredArgsConstructor

public class RedisService {

    /**
     * StringRedisTemplate 模板。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 获取底层 StringRedisTemplate。
     *
     * @return StringRedisTemplate
     */
    public StringRedisTemplate template() {
        return stringRedisTemplate;
    }

    /**
     * 设置字符串键值。
     *
     * @param key   键
     * @param value 值
     */
    public void set(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    /**
     * 设置字符串键值并指定过期时间。
     *
     * @param key   键
     * @param value 值
     * @param ttl   过期时间
     */
    public void set(String key, String value, Duration ttl) {
        stringRedisTemplate.opsForValue().set(key, value, ttl);
    }

    /**
     * 仅在键不存在时设置值，用于互斥或首次写入。
     *
     * @param key   键
     * @param value 值
     * @param ttl   过期时间
     * @return 是否设置成功
     */
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, value, ttl));
    }

    /**
     * 获取字符串值。
     *
     * @param key 键
     * @return 可选值
     */
    public Optional<String> get(String key) {
        return Optional.ofNullable(stringRedisTemplate.opsForValue().get(key));
    }

    /**
     * 将对象序列化为 JSON 后存储。
     *
     * @param key   键
     * @param value 对象值
     * @param ttl   过期时间
     * @param <T>   对象类型
     */
    public <T> void setObject(String key, T value, Duration ttl) {
        set(key, JsonUtils.toJson(value), ttl);
    }

    /**
     * 获取 JSON 字符串并反序列化为对象。
     *
     * @param key  键
     * @param type 目标类型
     * @param <T>  对象类型
     * @return 可选对象
     */
    public <T> Optional<T> getObject(String key, Class<T> type) {
        return get(key).map(json -> JsonUtils.fromJson(json, type));
    }

    /**
     * 对键做原子加 1。
     *
     * @param key 键
     * @return 加 1 后的值
     */
    public Long increment(String key) {
        return stringRedisTemplate.opsForValue().increment(key);
    }

    /**
     * 对键做原子增加指定增量。
     *
     * @param key   键
     * @param delta 增量
     * @return 增加后的值
     */
    public Long incrementBy(String key, long delta) {
        return stringRedisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * 对键做原子减少指定减量。
     *
     * @param key   键
     * @param delta 减量
     * @return 减少后的值
     */
    public Long decrementBy(String key, long delta) {
        return stringRedisTemplate.opsForValue().decrement(key, delta);
    }

    /**
     * 为键设置过期时间。
     *
     * @param key 键
     * @param ttl 过期时间
     */
    public void expire(String key, Duration ttl) {
        stringRedisTemplate.expire(key, ttl);
    }

    /**
     * 获取键的剩余过期时间（毫秒）。
     *
     * @param key 键
     * @return 剩余毫秒数，-1 表示未设置或已过期
     */
    public long ttlMillis(String key) {
        Long expire = stringRedisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
        return expire == null ? -1L : expire;
    }

    /**
     * 删除单个键。
     *
     * @param key 键
     * @return 是否删除成功
     */
    public Boolean delete(String key) {
        return stringRedisTemplate.delete(key);
    }

    /**
     * 批量删除键。
     *
     * @param keys 键集合
     * @return 删除数量
     */
    public Long delete(Collection<String> keys) {
        return stringRedisTemplate.delete(keys);
    }

    /**
     * 判断键是否存在。
     *
     * @param key 键
     * @return 是否存在
     */
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    /**
     * 按模式匹配查询键。
     *
     * @param pattern 匹配模式
     * @return 键集合
     */
    public Set<String> keys(String pattern) {
        return stringRedisTemplate.keys(pattern);
    }

    /**
     * 向指定频道发布消息。
     *
     * @param channel 频道
     * @param message 消息
     */
    public void publish(String channel, String message) {
        stringRedisTemplate.convertAndSend(channel, message);
    }

    /**
     * 读取整个 hash。用于结构化计数场景一次取回全部字段。
     *
     * @param key 键
     * @return 字段映射
     */
    public Map<String, String> hashGetAll(String key) {
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        if (entries == null || entries.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new java.util.LinkedHashMap<>();
        entries.forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
        return result;
    }

    /**
     * 批量写入 hash 字段并可设置过期时间。
     *
     * @param key    键
     * @param fields 字段映射
     * @param ttl    过期时间
     */
    public void hashPutAll(String key, Map<String, String> fields, Duration ttl) {
        stringRedisTemplate.opsForHash().putAll(key, fields);
        if (ttl != null) {
            stringRedisTemplate.expire(key, ttl);
        }
    }

    /**
     * 执行 Lua 脚本。返回 null 表示脚本没有返回有效结果，
     * 调用方必须显式处理，不能当成成功或零值。
     *
     * @param script Lua 脚本
     * @param keys   脚本键列表
     * @param args   脚本参数
     * @param <T>    返回值类型
     * @return 脚本执行结果
     */
    public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
        return stringRedisTemplate.execute(script, keys, toTextArgs(args));
    }

    /**
     * 执行脚本并以 long 返回，null 视为 0。
     * 仅适用于"无结果等价于零"的场景，涉及扣减库存这类操作不要用它。
     *
     * @param script Lua 脚本
     * @param keys   脚本键列表
     * @param args   脚本参数
     * @return 脚本执行结果 long 值
     */
    public long executeToLong(RedisScript<Long> script, List<String> keys, Object... args) {
        Long result = execute(script, keys, args);
        return result == null ? 0L : result;
    }

    /**
     * 参数统一转字符串，避免序列化差异导致 Lua 里取不到值或类型不符。
     *
     * @param args 原始参数
     * @return 字符串参数数组
     */
    private static Object[] toTextArgs(Object... args) {
        if (args == null || args.length == 0) {
            return new Object[0];
        }
        Object[] converted = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            converted[i] = args[i] == null ? null : String.valueOf(args[i]);
        }
        return converted;
    }

}
