package com.dong.lab.framework.cache;

public final /**
 * 空值标记。缓存用它表示"数据确实不存在"，
 * 与"缓存里没有"区分开，从而避免针对不存在 id 的重复回源。
 */
class CacheEmpty {

    public static final CacheEmpty INSTANCE = new CacheEmpty();

    private CacheEmpty() {
    }

    @Override
    public String toString() {
        return "CacheEmpty";
    }

}
