package com.dong.logging.dto;

import lombok.Data;

import java.util.List;

/**
 * 采集管道的运行状态。
 *
 * <p>日志管道和别的模块不一样：它出问题时不会报错，只会「安静地少数据」。
 * 所以队列积压、丢弃条数、最后一次刷写时间、最后一次错误，必须显式暴露出来。
 * 没有这组数字，「为什么今天的日志搜不到」只能靠猜。
 *
 * <p>丢弃是被允许的行为（缓冲有界），但必须是可观测的丢弃：
 * 丢了多少、是因为满了还是因为高水位降级，都要说得清楚。
 */
@Data
public class LogPipelineStats {

    /**
     * 缓冲容量。
     */
    private int bufferCapacity;

    /**
     * 当前积压条数。持续接近容量说明刷写跟不上产出。
     */
    private int bufferSize;

    /**
     * 缓冲水位，0~1。
     */
    private double bufferUsage;

    /**
     * 累计收到的日志条数。
     */
    private long received;

    /**
     * 累计丢弃的条数。
     */
    private long dropped;

    /**
     * 其中因为高水位被降级的条数。它占大头说明该扩容或者降级别，
     * 而不是继续让 ERROR 和 INFO 抢同一份配额。
     */
    private long droppedByWaterMark;

    /**
     * 累计写入 ES 的条数。
     */
    private long indexedDocs;

    /**
     * 失败的批次次数。
     */
    private long failedBatches;

    /**
     * 最后一次成功刷写的时间，毫秒时间戳，从未成功过为 0。
     */
    private long lastFlushAt;

    /**
     * 最后一次成功刷写的条数。
     */
    private int lastBatchSize;

    /**
     * 最后一次出错的时间，毫秒时间戳，从未出错为 0。
     */
    private long lastErrorAt;

    /**
     * 最后一次出错的原因。
     */
    private String lastError;

    /**
     * 读别名。
     */
    private String readAlias;

    /**
     * 当前正在写入的索引。
     */
    private String writeIndex;

    /**
     * 别名背后的全部按天索引，按名字正序。
     */
    private List<String> indices;

    /**
     * 索引里的日志总数。取不到（别名还没建）为 0。
     */
    private long documentCount;

    /**
     * 保留天数，超期的索引会被整块删掉。
     */
    private int retentionDays;

}
