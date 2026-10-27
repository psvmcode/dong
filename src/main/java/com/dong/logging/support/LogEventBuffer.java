package com.dong.logging.support;

import com.dong.logging.entity.LogDocument;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.LongAdder;

/**
 * 日志事件的进程内有界缓冲，是「打日志」与「写 ES」之间唯一的耦合点。
 *
 * <p>为什么必须有这一层：ES 写入是同步网络 IO，放在打日志的线程里做，
 * 一次慢查询就会把业务线程一起拖住；更糟的是 ES 客户端自己也会打日志，
 * 同步写会形成「打日志 → 写 ES → 打日志」的递归。解耦之后打日志只做一次
 * 入队，真正的网络 IO 交给 {@code LogShipperTask} 异步批量做。
 *
 * <p>为什么是静态单例而不是 Spring Bean：appender 由 logback 在 Spring 容器
 * 之前创建，拿不到任何 Bean，只能走静态入口。这是生命周期决定的，不是偷懒。
 *
 * <p>有界是硬要求。队列满了必须丢，而不是等着——日志是「尽力而为」的数据，
 * 让它反过来把应用拖垮或撑爆内存，是本末倒置。丢了多少是可观测的
 * （见 LogPipelineStats），丢得多了要靠容量与水位去调，而不是靠无限堆内存。
 *
 * <p>高水位降级：占用超过阈值后只保留 WARN 及以上。故障期间最不缺的就是
 * INFO，最缺的是那条说明故障原因的 ERROR，所以让低级别日志先让路。
 */
public final class LogEventBuffer {

    /**
     * 默认容量。appender 可以在 logback 配置里覆盖。
     */
    private static final int DEFAULT_CAPACITY = 10_000;

    /**
     * 高水位比例。占用超过它之后开始丢低级别日志。
     */
    private static final double HIGH_WATER_RATIO = 0.8;

    /**
     * 高水位之上仍然无条件保留的级别。
     */
    private static final Set<String> PRIORITY_LEVELS = Set.of("WARN", "ERROR");

    /**
     * 单例。
     */
    private static volatile LogEventBuffer INSTANCE;

    /**
     * appender 在 start 阶段写入的容量。
     */
    private static volatile int configuredCapacity = DEFAULT_CAPACITY;

    /**
     * 队列。
     */
    private final BlockingQueue<LogDocument> queue;

    /**
     * 队列容量。单独存一份：ArrayBlockingQueue 没有直接给容量的出口，
     * 而水位判断是每条日志都要走一次的路径，不该为它多拿两次锁。
     */
    private final int capacity;

    /**
     * 收到的事件数。
     */
    private final LongAdder received = new LongAdder();

    /**
     * 丢弃的事件数，含高水位降级。
     */
    private final LongAdder dropped = new LongAdder();

    /**
     * 其中因高水位被降级的条数。
     */
    private final LongAdder droppedByWaterMark = new LongAdder();

    private LogEventBuffer(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.capacity = capacity;
    }

    /**
     * 配置容量。只在 appender 的 start 阶段调用一次，晚于它再改没有意义——
     * 队列一旦建成，容量就改不了了。
     *
     * @param capacity 队列容量
     */
    public static void configure(int capacity) {
        if (capacity > 0) {
            configuredCapacity = capacity;
        }
    }

    /**
     * 取缓冲实例。
     *
     * @return 缓冲实例
     */
    public static LogEventBuffer instance() {
        LogEventBuffer buffer = INSTANCE;
        if (buffer == null) {
            synchronized (LogEventBuffer.class) {
                buffer = INSTANCE;
                if (buffer == null) {
                    buffer = new LogEventBuffer(configuredCapacity);
                    INSTANCE = buffer;
                }
            }
        }
        return buffer;
    }

    /**
     * 入队。满了就丢，绝不阻塞调用方。
     *
     * @param document 日志文档
     * @return 入队成功返回 true
     */
    public boolean offer(LogDocument document) {
        received.increment();
        if (overHighWater() && !PRIORITY_LEVELS.contains(document.getLevel())) {
            dropped.increment();
            droppedByWaterMark.increment();
            return false;
        }
        if (!queue.offer(document)) {
            dropped.increment();
            return false;
        }
        return true;
    }

    /**
     * 批量取出。用 drainTo 而不是逐条 poll：一次加锁搬一批，
     * 高频场景下锁竞争要低一个量级。
     *
     * @param sink 目标集合
     * @param max  最多取多少条
     * @return 实际取出的条数
     */
    public int drain(List<LogDocument> sink, int max) {
        return queue.drainTo(sink, max);
    }

    /**
     * 当前积压条数。
     *
     * @return 积压条数
     */
    public int size() {
        return queue.size();
    }

    /**
     * 队列容量。
     *
     * @return 容量
     */
    public int capacity() {
        return capacity;
    }

    /**
     * 水位，0~1。
     *
     * @return 水位
     */
    public double usage() {
        return capacity == 0 ? 0 : (double) queue.size() / capacity;
    }

    /**
     * 累计收到的条数。
     *
     * @return 收到条数
     */
    public long received() {
        return received.sum();
    }

    /**
     * 累计丢弃的条数。
     *
     * @return 丢弃条数
     */
    public long dropped() {
        return dropped.sum();
    }

    /**
     * 其中因高水位降级的条数。
     *
     * @return 高水位丢弃条数
     */
    public long droppedByWaterMark() {
        return droppedByWaterMark.sum();
    }

    /**
     * 是否已过高水位。
     *
     * @return 过高水位返回 true
     */
    private boolean overHighWater() {
        return queue.size() >= capacity * HIGH_WATER_RATIO;
    }

}
