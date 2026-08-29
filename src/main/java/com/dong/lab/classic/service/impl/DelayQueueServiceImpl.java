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

@Slf4j
@Service
public class DelayQueueServiceImpl implements DelayQueueService {

    private static final String QUEUE = "lab:delay:queue";

    private final RDelayedQueue<String> delayedQueue;

    public DelayQueueServiceImpl(RedissonClient redissonClient) {
        RBlockingQueue<String> blockingQueue = redissonClient.getBlockingQueue(QUEUE);
        this.delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
    }

    @Override
    public void offer(String payload, Duration delay) {
        delayedQueue.offer(payload, delay.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        log.info("delay queue offered payload={} delay={}ms", payload, delay.toMillis());
    }

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

    @Override
    public long size() {
        return delayedQueue.size();
    }

}
