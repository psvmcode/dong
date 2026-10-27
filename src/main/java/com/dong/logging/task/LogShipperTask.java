package com.dong.logging.task;

import com.dong.logging.service.LogIngestService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 把缓冲里的日志批量刷进 ES。
 *
 * <p>为什么是定时任务而不是来一条写一条：写入是网络 IO，
 * 放在打日志的线程里做会把业务线程一起拖住；批量写则把 N 次往返压成 1 次，
 * 代价是日志可见性有一个刷写周期的延迟——对日志来说这个延迟完全可以接受。
 *
 * <p>失败只告警不重试。ES 不可用期间重试没有意义，还会把一次故障放大成错误风暴，
 * 所以这里退避到配置的间隔之后才再报一次。丢掉的那一批就丢了，
 * 管道状态里看得到 dropped 与 failedBatches，事后能核对。
 *
 * <p>停机前补一次刷写：进程退出时缓冲里剩下的，是「刚刚发生但还没进索引」的那批，
 * 恰恰是最可能被翻出来看的一批。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"dong.logging.enabled", "dong.elasticsearch.enabled"}, havingValue = "true")
public class LogShipperTask {

    /**
     * ingestService，日志写入服务。
     */
    private final LogIngestService ingestService;

    /**
     * 单次最多刷多少条。
     */
    @Value("${dong.logging.flush-max-batch:500}")
    private int flushMaxBatch;

    /**
     * 失败告警的退避时长，毫秒。
     */
    @Value("${dong.logging.retry-backoff-ms:10000}")
    private long retryBackoffMs;

    /**
     * 下一次允许告警的时间。
     */
    private final AtomicLong nextWarnAt = new AtomicLong();

    /**
     * 定时刷写。用 fixedDelay 而不是 fixedRate：上一轮没跑完就不开下一轮，
     * 避免 ES 慢的时候越堆越多。
     */
    @Scheduled(fixedDelayString = "${dong.logging.flush-interval-ms:1000}")
    public void ship() {
        try {
            int written = ingestService.flush(flushMaxBatch);
            if (written > 0) {
                log.debug("shipped {} log documents", written);
            }
        } catch (Exception ex) {
            warnOnce(ex);
        }
    }

    /**
     * 停机前把缓冲排空。
     */
    @PreDestroy
    public void shipOnShutdown() {
        try {
            ingestService.flush(Integer.MAX_VALUE);
        } catch (Exception ex) {
            log.warn("flush logs on shutdown failed: {}", ex.getMessage());
        }
    }

    /**
     * 按退避节奏报一次失败。调度线程会吞掉异常，不打日志的话任务就是「静默地不再工作」。
     *
     * @param ex 异常
     */
    private void warnOnce(Exception ex) {
        long now = System.currentTimeMillis();
        long next = nextWarnAt.get();
        if (now < next) {
            return;
        }
        if (nextWarnAt.compareAndSet(next, now + retryBackoffMs)) {
            log.warn("ship logs failed, backing off: {}", ex.getMessage());
        }
    }

}
