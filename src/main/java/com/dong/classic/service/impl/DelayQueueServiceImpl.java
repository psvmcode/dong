package com.dong.classic.service.impl;

import com.dong.classic.service.DelayQueueService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
/**
 * 延迟队列实现。基于 Redisson 的延迟队列，
 * 到期后转入目标队列再被消费。
 *
 * <p>Redis 的延迟队列没有重试与持久化保证，强可靠场景应使用 RocketMQ 延迟消息。
 */
@Slf4j
@Service

public class DelayQueueServiceImpl implements DelayQueueService {

    /**
     * 延迟队列名称。Redis 延迟队列没有重试与持久化保证，
     * 因此投递与消费都会同步落库，靠数据库兜住可靠性。
     */
    private static final String QUEUE = "lab:delay:queue";

    /**
     * 任务状态：待投递，已落库但尚未进入队列。
     */
    private static final int STATUS_PENDING = 1;

    /**
     * 任务状态：已投递，已进入 Redis 延迟队列等待触发。
     */
    private static final int STATUS_OFFERED = 2;

    /**
     * 任务状态：已消费。
     */
    private static final int STATUS_CONSUMED = 3;

    /**
     * Redisson 延迟队列。
     */
    private final RDelayedQueue<String> delayedQueue;

    /**
     * 延迟任务数据访问，用于追踪任务去向并做补偿重投。
     */
    private final com.dong.classic.mapper.ClassicDelayTaskMapper delayTaskMapper;

    /**
     * 雪花发号器，生成任务编号。
     */
    private final com.dong.common.util.Snowflake snowflake;

    public DelayQueueServiceImpl(RedissonClient redissonClient,
                                 com.dong.classic.mapper.ClassicDelayTaskMapper delayTaskMapper,
                                 com.dong.common.util.Snowflake snowflake) {
        RBlockingQueue<String> blockingQueue = redissonClient.getBlockingQueue(QUEUE);
        this.delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
        this.delayTaskMapper = delayTaskMapper;
        this.snowflake = snowflake;
    }

    /**
     * 投递延迟任务。先落库再入队，顺序不能反：
     * 入队成功后才落库的话，一旦落库失败就再也查不到这个任务了。
     *
     * @param payload 任务内容
     * @param delay   延迟时间
     * @return 任务编号，便于追踪
     */
    @Override
    public String offer(String payload, Duration delay) {
        String taskNo = "DT" + snowflake.nextId();
        com.dong.classic.entity.ClassicDelayTask task =
                new com.dong.classic.entity.ClassicDelayTask();
        task.setTaskNo(taskNo);
        task.setPayload(payload);
        task.setStatus(STATUS_PENDING);
        task.setExpectTime(java.time.LocalDateTime.now().plus(delay));
        task.setRetryCount(0);
        delayTaskMapper.insert(task);
        delayedQueue.offer(payload, delay.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        delayTaskMapper.updateStatus(taskNo, STATUS_OFFERED, null);
        log.info("delay queue offered taskNo={} payload={} delay={}ms", taskNo, payload, delay.toMillis());
        return taskNo;
    }

    /**
     * 取出已到期的任务，并把对应记录标记为已消费。
     *
     * @param limit 最大取出数量
     * @return 任务内容列表
     */
    @Override
    public List<String> take(int limit) {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            String item = delayedQueue.poll();
            if (item == null) {
                break;
            }
            items.add(item);
            markConsumed(item);
        }
        return items;
    }

    /**
     * 任务被消费后回写状态。队列里只有 payload，没有任务编号，
     * 因此按 payload 反查一条未消费的记录来标记。
     * 失败只记录日志，消费已经完成，不该因为回写失败而丢掉任务内容。
     *
     * @param payload 任务内容
     */
    private void markConsumed(String payload) {
        try {
            delayTaskMapper.markConsumedByPayload(payload, java.time.LocalDateTime.now());
        } catch (Exception ex) {
            log.error("mark delay task consumed failed payload={}", payload, ex);
        }
    }

    /**
     * 查询待消费任务数量。
     *
     * @return 待消费任务数量
     */
    @Override
    public long size() {
        return delayedQueue.size();
    }

}
