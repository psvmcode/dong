package com.dong.framework.cache;

/**
 * 缓存查询结果，用密封接口表达四态，避免用 null 承载多重语义。
 *
 * <p>四者语义必须严格区分：
 * <ul>
 *     <li>Miss 是缓存里完全没有，需要回源</li>
 *     <li>Empty 是缓存里有空值标记，说明数据确实不存在，不应回源</li>
 *     <li>Hit 是真正命中</li>
 *     <li>Stale 是逻辑已过期但物理仍在，只在回源失败时用来兜底</li>
 * </ul>
 * 混淆 Miss 与 Empty 会让防穿透统计完全失真，
 * 把 Stale 当 Hit 用则会让过期数据无声地一直返回下去。
 */
public sealed interface CacheLookup<T> {

    record Hit<T>(T value) implements CacheLookup<T> {
    }

    record Stale<T>(T value) implements CacheLookup<T> {
    }

    record Empty<T>() implements CacheLookup<T> {
    }

    record Miss<T>() implements CacheLookup<T> {
    }

}
