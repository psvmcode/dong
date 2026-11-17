package com.dong.common.util;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 雪花发号器。
 *
 * <p>时钟回拨不再直接报错：NTP 校时或虚拟机迁移导致的回拨会让所有写路径
 * 同时失败，代价远大于让发号器短暂使用逻辑时钟。回拨期间保持在同一毫秒上
 * 递增序列号，溢出后再推进虚拟时间；真实时间追上后自动切回，ID 始终单调递增。
 */
@Slf4j
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
     * 是否处于回拨后的逻辑时钟模式。
     */
    private boolean logicalClock = false;

    /**
     * 构造雪花发号器。
     *
     * @param workerId     工作节点 id
     * @param dataCenterId 数据中心 id
     */
    public Snowflake(@Value("${dong.snowflake.worker-id:1}") long workerId, @Value("${dong.snowflake.data-center-id:1}") long dataCenterId) {
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
        if (timestamp < lastTimestamp) {
            long backward = lastTimestamp - timestamp;
            if (backward <= MAX_BACKWARD_MS) {
                timestamp = waitUntilNextMillis(lastTimestamp);
                logicalClock = false;
            } else {
                // 回拨幅度较大时停在同一毫秒上继续发号，序列号溢出才推进虚拟时间。
                // 不能等待真实时间追上：一次大回拨可能要等几分钟，写路径会全部挂死。
                if (!logicalClock) {
                    log.warn("snowflake clock moved backwards {}ms, switch to logical clock", backward);
                }
                timestamp = lastTimestamp;
                logicalClock = true;
            }
        } else {
            logicalClock = false;
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0L) {
                timestamp = logicalClock ? lastTimestamp + 1L : waitUntilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT) | (dataCenterId << DATA_CENTER_ID_SHIFT) | (workerId << WORKER_ID_SHIFT) | sequence;
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
