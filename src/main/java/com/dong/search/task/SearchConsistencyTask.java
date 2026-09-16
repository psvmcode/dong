package com.dong.search.task;

import com.dong.search.service.SearchSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
/**
 * 索引一致性兜底任务。
 *
 * <p>实时同步覆盖不了的情况都得靠它收敛：同步时 ES 不可用、进程在事务提交后立刻挂掉、
 * 有人绕过应用直接改库或删索引。这三种情况都会让库与索引漂移，而且都不会自己恢复。
 *
 * <p>没有差异时不打日志，避免每轮都刷一条「一切正常」。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "dong.elasticsearch", name = "enabled", havingValue = "true")
@RequiredArgsConstructor

public class SearchConsistencyTask {

    /**
     * searchSyncService，索引同步服务。
     */
    private final SearchSyncService searchSyncService;

    /**
     * 定期对账并自动修复。修了什么由同步服务负责打日志，这里只兜住异常：
     * 定时任务抛出的异常会被调度线程直接吞掉，不打日志就只能看到任务「静默地不再工作」。
     */
    @Scheduled(fixedDelayString = "${dong.elasticsearch.reconcile-interval-ms:600000}",
            initialDelayString = "${dong.elasticsearch.reconcile-initial-delay-ms:60000}")
    public void reconcile() {
        try {
            searchSyncService.repairConsistency();
        } catch (Exception ex) {
            log.error("search index reconciliation failed", ex);
        }
    }

}
