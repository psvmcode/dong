package com.dong.lab.classic.service.impl;

import com.dong.lab.classic.service.DelayQueueService;
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

    private static final String QUEUE = "lab:delay:queue";

    /**
     * delayedQueue。
     */
    private final RDelayedQueue<String> delayedQueue;

    public DelayQueueServiceImpl(RedissonClient redissonClient) {
        RBlockingQueue<String> blockingQueue = redissonClient.getBlockingQueue(QUEUE);
        this.delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
    }

    /**
     * 投递延迟任务。
     *
     * @param payload 任务内容
     * @param delay   延迟时间
     */
    @Override
    public void offer(String payload, Duration delay) {
        delayedQueue.offer(payload, delay.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        log.info("delay queue offered payload={} delay={}ms", payload, delay.toMillis());
    }

    /**
     * 取出已到期的任务。
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
        }
        return items;
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
