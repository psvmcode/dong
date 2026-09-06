package com.dong.framework.cache;

/**
 * 缓存查询结果，用密封接口表达三态，避免用 null 承载多重语义。
 *
 * <p>三者语义必须严格区分：
 * Miss 是缓存里完全没有，需要回源；
 * Empty 是缓存里有空值标记，说明数据确实不存在，不应回源；
 * Hit 是真正命中。
 * 混淆 Miss 与 Empty 会让防穿透的统计完全失真。
 */
public sealed interface CacheLookup<T> {

    record Hit<T>(T value) implements CacheLookup<T> {
    }

    record Empty<T>() implements CacheLookup<T> {
    }

    record Miss<T>() implements CacheLookup<T> {
    }

}
