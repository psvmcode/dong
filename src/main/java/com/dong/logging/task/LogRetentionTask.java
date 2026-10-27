package com.dong.logging.task;

import com.dong.logging.service.LogIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 清理超出保留天数的日志索引。
 *
 * <p>整块删索引是这里唯一合理的清理方式：delete_by_query 要逐条标记删除再等段合并，
 * 代价高一个数量级，而且删完之后磁盘也不会立刻还回来。
 * 按天切索引的意义就在于让「保留 N 天」变成删几个索引。
 *
 * <p>保留天数与 logback 落盘文件的 maxHistory 对齐（默认都是 14）：
 * 两边不一致时会出现「文件里还有、索引里已经没了」这种说不清的缺口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"dong.logging.enabled", "dong.elasticsearch.enabled"}, havingValue = "true")
public class LogRetentionTask {

    /**
     * ingestService，日志写入服务。
     */
    private final LogIngestService ingestService;

    /**
     * 清理过期索引。
     */
    @Scheduled(fixedDelayString = "${dong.logging.retention-interval-ms:3600000}", initialDelayString = "${dong.logging.retention-initial-delay-ms:60000}")
    public void cleanup() {
        try {
            int deleted = ingestService.cleanupExpired();
            if (deleted > 0) {
                log.info("deleted {} expired log indices", deleted);
            }
        } catch (Exception ex) {
            log.error("cleanup expired log indices failed", ex);
        }
    }

}
