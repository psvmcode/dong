package com.dong.cache.dto;

import com.dong.cache.entity.Product;

/**
 * 商品读取结果。把降级信息一并带出去，
 * 调用方据此决定是照常展示，还是提示「数据可能不是最新的」。
 *
 * <p>之所以不直接返回 Product：降级值和正常值在类型上完全一样，
 * 调用方无从分辨。让它俩混在一起，降级就变成了静默返回错数据。
 */
public record ProductReadResult(Product product, boolean stale) {

    /**
     * 构造正常读取结果。
     *
     * @param product 商品实体
     * @return 读取结果
     */
    public static ProductReadResult fresh(Product product) {
        return new ProductReadResult(product, false);
    }

    /**
     * 构造降级读取结果，值为过期旧数据。
     *
     * @param product 商品实体
     * @return 读取结果
     */
    public static ProductReadResult stale(Product product) {
        return new ProductReadResult(product, true);
    }

}
