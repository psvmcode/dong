package com.dong.search.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 聚合统计结果。一次查询里同时跑四种聚合：分面、数值统计、区间分布、时间直方图，
 * 外加一个「分面里再挂数值统计」的子聚合。
 *
 * <p>聚合和检索走的是两套完全不同的数据结构：检索查倒排，聚合读 doc_values（列存）。
 * 所以聚合字段必须是 keyword / 数值 / 日期这类有 doc_values 的类型，
 * text 字段默认没有 doc_values，直接对它聚合会报错——这也是 category 必须声明成 keyword 的原因。
 *
 * <p>聚合是在每个分片上分别算再合并的，结果是近似值：
 * terms 聚合的 docCount 在分片多的时候可能不准，需要靠 shard_size 调精度。
 */
@Data
public class SearchAggregateResponse {

    /**
     * 参与统计的文档数。
     */
    private long total;

    /**
     * 分类分面：分类名到商品数，给前端做筛选标签。
     */
    private Map<String, Long> categoryFacets;

    /**
     * 全量价格统计（最小值、最大值、平均值、总和）。
     */
    private Stats priceStats;

    /**
     * 价格区间分布，给前端画价格筛选条。
     */
    private List<Bucket> priceRanges;

    /**
     * 按月统计的新增商品数，时间趋势用。
     */
    private List<Bucket> monthlyBuckets;

    /**
     * 每个分类各自的价格统计。这是 terms 下面挂 stats 的子聚合，
     * 一次查询就能拿到「各分类的均价」，不用按分类查 N 次。
     */
    private Map<String, Stats> priceStatsByCategory;

    /**
     * 数值统计结果。
     */
    @Data
    public static class Stats {

        /**
         * 参与统计的文档数。
         */
        private long count;

        /**
         * 最小值。
         */
        private Double min;

        /**
         * 最大值。
         */
        private Double max;

        /**
         * 平均值。
         */
        private Double avg;

        /**
         * 总和。
         */
        private Double sum;

    }

    /**
     * 聚合桶。区间聚合的 key 形如 *-100.0、100.0-500.0，
     * 时间直方图的 key 是 ES 返回的日期字符串。
     */
    @Data
    public static class Bucket {

        /**
         * 桶的键。
         */
        private String key;

        /**
         * 落在这个桶里的文档数。
         */
        private long docCount;

    }

}
