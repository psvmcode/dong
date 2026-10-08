package com.dong.upload.task;

import com.dong.upload.service.UploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 清理过期未完成的上传任务。
 *
 * <p>用户传一半关掉页面是最常见的情况，那些分片会一直留在磁盘上。
 * 没有这个任务，一个长期运行的实例会把磁盘慢慢吃掉，而且没有任何人会发现。
 *
 * <p>方法体必须 try/catch：调度线程会吞掉异常，不打日志就只能看到任务「静默地不再工作」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dong.upload", name = "cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class UploadCleanupTask {

    /**
     * uploadService，业务服务层。
     */
    private final UploadService uploadService;

    /**
     * 超过多少小时未完成算过期。
     */
    @Value("${dong.upload.cleanup-expire-hours:24}")
    private int expireHours;

    /**
     * 单次最多清理多少条，防止一次扫全表把数据库拖住。
     */
    @Value("${dong.upload.cleanup-limit:100}")
    private int limit;

    /**
     * 周期性清理过期任务。
     */
    @Scheduled(fixedDelayString = "${dong.upload.cleanup-interval-ms:3600000}",
            initialDelayString = "${dong.upload.cleanup-initial-delay-ms:60000}")
    public void cleanup() {
        try {
            int removed = uploadService.cleanupExpired(expireHours, limit);
            if (removed > 0) {
                log.info("upload cleanup task removed={} expireHours={}", removed, expireHours);
            }
        } catch (Exception ex) {
            log.error("upload cleanup task failed", ex);
        }
    }

}
