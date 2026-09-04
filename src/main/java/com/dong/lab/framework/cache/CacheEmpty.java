package com.dong.lab.framework.cache;

/**
 * 空值标记。缓存用它表示"数据确实不存在"，
 * 与"缓存里没有"区分开，从而避免针对不存在 id 的重复回源。
 */
public final class CacheEmpty {

    /**
     * 单例实例。
     */
    public static final CacheEmpty INSTANCE = new CacheEmpty();

    /**
     * 私有构造方法，禁止外部实例化。
     */
    private CacheEmpty() {
    }

    /**
     * 返回字符串表示。
     */
    @Override
    public String toString() {
        return "CacheEmpty";
    }

}
