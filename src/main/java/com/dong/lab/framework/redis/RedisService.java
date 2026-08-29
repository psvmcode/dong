package com.dong.lab.framework.redis;

import com.dong.lab.common.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisService {

    private final StringRedisTemplate stringRedisTemplate;

    public StringRedisTemplate template() {
        return stringRedisTemplate;
    }

    public void set(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, String value, Duration ttl) {
        stringRedisTemplate.opsForValue().set(key, value, ttl);
    }

    public boolean setIfAbsent(String key, String value, Duration ttl) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, value, ttl));
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(stringRedisTemplate.opsForValue().get(key));
    }

    public <T> void setObject(String key, T value, Duration ttl) {
        set(key, JsonUtils.toJson(value), ttl);
    }

    public <T> Optional<T> getObject(String key, Class<T> type) {
        return get(key).map(json -> JsonUtils.fromJson(json, type));
    }

    public Long increment(String key) {
        return stringRedisTemplate.opsForValue().increment(key);
    }

    public Long incrementBy(String key, long delta) {
        return stringRedisTemplate.opsForValue().increment(key, delta);
    }

    public Long decrementBy(String key, long delta) {
        return stringRedisTemplate.opsForValue().decrement(key, delta);
    }

    public void expire(String key, Duration ttl) {
        stringRedisTemplate.expire(key, ttl);
    }

    public long ttlMillis(String key) {
        Long expire = stringRedisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
        return expire == null ? -1L : expire;
    }

    public Boolean delete(String key) {
        return stringRedisTemplate.delete(key);
    }

    public Long delete(Collection<String> keys) {
        return stringRedisTemplate.delete(keys);
    }

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    public Set<String> keys(String pattern) {
        return stringRedisTemplate.keys(pattern);
    }

    public void publish(String channel, String message) {
        stringRedisTemplate.convertAndSend(channel, message);
    }

    public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
        return stringRedisTemplate.execute(script, keys, toTextArgs(args));
    }

    public long executeToLong(RedisScript<Long> script, List<String> keys, Object... args) {
        Long result = execute(script, keys, args);
        return result == null ? 0L : result;
    }

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
