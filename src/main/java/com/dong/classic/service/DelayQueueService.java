package com.dong.classic.service;

import java.time.Duration;
import java.util.List;

/**
 * 延迟队列。基于 Redisson 的 RDelayedQueue，
 * 适合超时未支付自动关单这类场景。
 *
 * <p>注意 Redis 的延迟队列没有重试和持久化保证，
 * 进程重启或 Redis 故障都可能丢消息，强可靠场景应用 RocketMQ 的延迟消息。
 */
public interface DelayQueueService {

    /**
     * 投递任务，延迟指定时长后可被消费。
     */
    void offer(String payload, Duration delay);

    /**
     * 取出已到期的任务，未到期的不返回。
     */
    List<String> take(int limit);

    /**
     * 待消费任务数量。
     */
    long size();

}
