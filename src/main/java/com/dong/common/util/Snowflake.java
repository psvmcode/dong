package com.dong.common.util;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
/**
 * 雪花发号器。
 */
@Component

public class Snowflake {

    private static final long MAX_BACKWARD_MS = 5L;

    private static final long EPOCH = 1735689600000L;

    private static final long WORKER_ID_BITS = 5L;

    private static final long DATA_CENTER_ID_BITS = 5L;

    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);

    private static final long MAX_DATA_CENTER_ID = ~(-1L << DATA_CENTER_ID_BITS);

    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;

    private static final long DATA_CENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATA_CENTER_ID_BITS;

    /**
     * 工作节点 id。
     */
    private final long workerId;

    /**
     * 数据中心 id。
     */
    private final long dataCenterId;

    /**
     * 序列号。
     */
    private long sequence = 0L;

    /**
     * 上次生成 id 的时间戳。
     */
    private long lastTimestamp = -1L;

    /**
     * 构造雪花发号器。
     *
     * @param workerId     工作节点 id
     * @param dataCenterId 数据中心 id
     */
    public Snowflake(@Value("${dong.snowflake.worker-id:1}") long workerId,
                     @Value("${dong.snowflake.data-center-id:1}") long dataCenterId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID || dataCenterId < 0 || dataCenterId > MAX_DATA_CENTER_ID) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "worker id or data center id out of range");
        }
        this.workerId = workerId;
        this.dataCenterId = dataCenterId;
    }

    /**
     * 生成下一个 id。
     *
     * @return 全局唯一 id
     */
    public synchronized long nextId() {
        long timestamp = currentMillis();
        long offset = timestamp - lastTimestamp;
        if (offset < 0) {
            if (offset >= -MAX_BACKWARD_MS) {
                timestamp = waitUntilNextMillis(lastTimestamp);
            } else {
                throw new BusinessException(Constants.CODE_INTERNAL_ERROR, "clock moved backwards");
            }
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0L) {
                timestamp = waitUntilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (dataCenterId << DATA_CENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /**
     * 生成下一个 id 的字符串形式。
     *
     * @return 全局唯一 id 字符串
     */
    public String nextIdStr() {
        return Long.toString(nextId());
    }

    /**
     * 等待下一毫秒。
     *
     * @param lastTs 上次时间戳
     * @return 当前时间戳
     */
    private long waitUntilNextMillis(long lastTs) {
        long timestamp = currentMillis();
        while (timestamp <= lastTs) {
            Thread.yield();
            timestamp = currentMillis();
        }
        return timestamp;
    }

    /**
     * 获取当前毫秒时间戳。
     *
     * @return 当前毫秒时间戳
     */
    private long currentMillis() {
        return System.currentTimeMillis();
    }

}
