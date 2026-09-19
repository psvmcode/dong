package com.dong.search.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 地理检索结果。按与给定坐标的距离由近到远返回。
 *
 * <p>geo_point 字段和别的类型不一样：它不是靠倒排查的，
 * 而是把经纬度编码进 BKD 树，所以能做「半径内过滤」和「按距离排序」这两件事。
 *
 * <p>注意没填经纬度的商品会被 geo_distance 直接排除，
 * 这类文档在 ES 里相当于没有 location，不会出现在任何距离查询的结果里。
 */
@Data
public class NearbySearchResponse {

    /**
     * 半径内命中的商品数。
     */
    private long total;

    /**
     * 由近到远的商品列表。
     */
    private List<NearbyHit> list;

    /**
     * 带距离的命中项。
     */
    @Data
    public static class NearbyHit {

        /**
         * 商品 id。
         */
        private String id;

        /**
         * 商品名称。
         */
        private String name;

        /**
         * 商品分类。
         */
        private String category;

        /**
         * 商品价格。
         */
        private BigDecimal price;

        /**
         * 与查询坐标的距离，单位公里。这个值来自 _geo_distance 排序，
         * 不是文档字段，每次查询现算。
         */
        private Double distanceKm;

    }

}
