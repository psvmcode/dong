package com.dong.ad.task;

import com.dong.ad.service.AdCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 在线数据保留期任务。
 *
 * <p>事件与流水带幂等唯一键，天然只增不减。不清理的话在线表会越来越慢，
 * 最终把召回与计费一起拖住。归档顺序是「先写归档表再删在线表」，
 * 且只删除确认已归档的行，因此归档失败不会丢账。
 *
 * <p>方法体整体 try/catch：调度线程会吞掉异常，不打日志就只能看到任务静默停止。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dong.ad", name = "retention-task-enabled", havingValue = "true", matchIfMissing = true)
public class AdDataRetentionTask {

    /**
     * adCleanupService，业务服务层。
     */
    private final AdCleanupService adCleanupService;

    /**
     * 在线数据保留天数。
     */
    @Value("${dong.ad.retention-days:7}")
    private int retentionDays;

    /**
     * 单批处理条数。
     */
    @Value("${dong.ad.retention-batch-size:200}")
    private int batchSize;

    /**
     * 周期性归档过期数据。
     */
    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 120_000L)
    @com.dong.framework.lock.DistributedTaskLock(value = "ad-retention", leaseMs = 300_000L)
    public void cleanup() {
        try {
            LocalDate before = LocalDate.now().minusDays(Math.max(1, retentionDays));
            int archived = adCleanupService.archiveExpiredCharge(before, batchSize);
            int removed = adCleanupService.deleteExpiredEvents(before, batchSize);
            if (archived > 0 || removed > 0) {
                log.info("ad retention task archived={} eventsRemoved={} before={}", archived, removed, before);
            }
        } catch (Exception ex) {
            log.error("ad retention task failed", ex);
        }
    }

}
