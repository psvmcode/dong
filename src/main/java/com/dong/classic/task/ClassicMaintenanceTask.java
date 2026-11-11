package com.dong.classic.task;

import com.dong.classic.entity.ClassicDelayTask;
import com.dong.classic.mapper.ClassicDelayTaskMapper;
import com.dong.classic.service.DelayQueueService;
import com.dong.classic.service.ShortLinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
/**
 * 经典场景的维护任务。
 *
 * <p>这里的两件事都是「缓存负责性能、数据库负责可靠」的收口动作：
 * 点击计数在 Redis 里累加，定时任务负责把它落库；
 * 延迟任务在 Redis 队列里等待，定时任务负责找出那些到了点却没被消费的。
 * 少了任何一个，数据就只剩缓存里的一份，随时可能丢。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dong.classic", name = "maintenance-enabled", havingValue = "true", matchIfMissing = true)
public class ClassicMaintenanceTask {

    /**
     * 扫描超时未消费任务时的窗口，只处理预计时间已经过去这么久的，
     * 给正常消费留出余量，避免误补偿刚刚投递的任务。
     */
    private static final Duration DELAY_GRACE = Duration.ofMinutes(1);

    /**
     * 单轮最多补偿的任务数，防止一次处理过多拖慢任务。
     */
    private static final int DELAY_LIMIT = 50;

    /**
     * 重投次数上限。超过后标记失败终态，避免一条永远无人消费的任务
     * 每分钟都被重投一次，把队列和日志撑爆。
     */
    private static final int MAX_RETRY = 5;

    /**
     * 补偿重投的再次到期时间。顺延一个扫描周期，否则原记录
     * 仍处于超时状态，下一轮扫描会重复入队同一份 payload。
     */
    private static final Duration REPRIORITIZE_DELAY = Duration.ofMinutes(1);

    /**
     * 短链接服务，业务服务层。
     */
    private final ShortLinkService shortLinkService;

    /**
     * 延迟队列服务，业务服务层。
     */
    private final DelayQueueService delayQueueService;

    /**
     * 延迟任务数据访问，MyBatis Mapper 数据访问层。
     */
    private final ClassicDelayTaskMapper delayTaskMapper;

    /**
     * 把缓存里的点击计数回写数据库。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void flushShortLinkHits() {
        try {
            shortLinkService.flushHitCount();
        } catch (Exception ex) {
            log.error("flushShortLinkHits task failed", ex);
        }
    }

    /**
     * 补偿超时未消费的延迟任务。
     *
     * <p>Redis 延迟队列没有重试保证，任务可能因为进程重启或消费失败而丢失。
     * 这里按预计时间扫描那些还没被标记消费的任务，只把原任务的 payload
     * 重新入队（不新建任务记录），顺延预计时间一个扫描周期并递增重投次数。
     * 重投超限后标记失败终态，等待人工介入。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 90_000)
    public void compensateDelayTasks() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minus(DELAY_GRACE);
            for (ClassicDelayTask task : delayTaskMapper.selectOverdue(threshold, DELAY_LIMIT)) {
                try {
                    if (task.getRetryCount() >= MAX_RETRY) {
                        delayTaskMapper.markFailed(task.getTaskNo());
                        log.error("delay task retry exhausted, marked failed taskNo={} payload={}", task.getTaskNo(), task.getPayload());
                        continue;
                    }
                    delayQueueService.reoffer(task.getPayload(), REPRIORITIZE_DELAY);
                    delayTaskMapper.increaseRetry(task.getTaskNo());
                    delayTaskMapper.postpone(task.getTaskNo(), LocalDateTime.now().plus(REPRIORITIZE_DELAY));
                    log.warn("delay task re-offered taskNo={} retry={}", task.getTaskNo(), task.getRetryCount() + 1);
                } catch (Exception ex) {
                    log.error("compensate delay task failed taskNo={}", task.getTaskNo(), ex);
                }
            }
        } catch (Exception ex) {
            log.error("compensateDelayTasks task failed", ex);
        }
    }

}
