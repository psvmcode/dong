package com.dong.framework.cache;

/**
 * 多级缓存的读取结果。调用方靠它区分「没有数据」与「拿不到数据」，
 * 这两种情况对用户的含义完全不同：前者是确定的答案，后者需要提示稍后重试。
 *
 * <p>Stale 是降级态：缓存里的值已经逻辑过期，且回源失败，
 * 只能把旧值交出去。返回时必须让调用方知道数据是旧的，
 * 否则「降级」就变成了「静默返回错误数据」。
 */
public sealed interface CacheResolution<T> {

    /**
     * 正常结果，来自缓存命中或回源成功。
     *
     * @param value 数据值
     * @param <T>   数据类型
     */
    record Fresh<T>(T value) implements CacheResolution<T> {
    }

    /**
     * 降级结果。回源失败时交出的过期旧值。
     *
     * @param value 过期数据值
     * @param <T>   数据类型
     */
    record Stale<T>(T value) implements CacheResolution<T> {
    }

    /**
     * 确认不存在，由空值标记或布隆过滤器判定，不要再回源。
     *
     * @param <T> 数据类型
     */
    record Absent<T>() implements CacheResolution<T> {
    }

    /**
     * 依赖不可用，且连旧值都没有，只能明确失败。
     *
     * @param <T> 数据类型
     */
    record Unavailable<T>() implements CacheResolution<T> {
    }

}
