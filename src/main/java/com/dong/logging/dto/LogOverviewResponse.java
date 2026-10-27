package com.dong.logging.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 仪表盘数据。一次查询同时跑四种聚合，对应看日志的四个视角：
 * 级别分布看健康度、时间直方图看什么时候出的问题、Top logger 看哪个模块在刷、
 * Top 异常类型看问题集中在哪。
 *
 * <p>与检索共用同一套过滤条件，所以「图上看到的」和「列表里查到的」永远一致。
 * size 设成 0，只要聚合不要文档——仪表盘不需要原文，省掉一轮文档抓取。
 */
@Data
public class LogOverviewResponse {

    /**
     * 参与统计的日志条数。
     */
    private long total;

    /**
     * 级别分布，级别名到条数。
     */
    private Map<String, Long> levelCounts;

    /**
     * 时间直方图，按时间正序。
     */
    private List<TimelineBucket> timeline;

    /**
     * 日志量最高的 logger，最多 10 个。
     */
    private Map<String, Long> topLoggers;

    /**
     * ERROR 里出现最多的异常类型，最多 10 个。
     */
    private Map<String, Long> topErrorTypes;

    /**
     * ERROR 级别的总条数，用来算错误率。
     */
    private long errorCount;

    /**
     * 直方图的一个桶。
     */
    @Data
    public static class TimelineBucket {

        /**
         * 桶的起始时刻，毫秒时间戳。用时间戳而不是格式化字符串：
         * 时区交给前端按本地时区渲染，后端只负责给准确的时间点。
         */
        private long timeMillis;

        /**
         * 这个桶里的总条数。
         */
        private long total;

        /**
         * 这个桶里按级别拆分的条数，用来画堆叠柱。
         */
        private Map<String, Long> levelCounts;

    }

}
