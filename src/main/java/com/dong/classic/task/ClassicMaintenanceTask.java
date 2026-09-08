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
        shortLinkService.flushHitCount();
    }

    /**
     * 补偿超时未消费的延迟任务。
     *
     * <p>Redis 延迟队列没有重试保证，任务可能因为进程重启或消费失败而丢失。
     * 这里按预计时间扫描那些还没被标记消费的任务，重新投递并递增重投次数。
     * 次数本身不设上限，但会一直打日志，便于人工介入。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 90_000)
    public void compensateDelayTasks() {
        LocalDateTime threshold = LocalDateTime.now().minus(DELAY_GRACE);
        for (ClassicDelayTask task : delayTaskMapper.selectOverdue(threshold, DELAY_LIMIT)) {
            try {
                delayQueueService.offer(task.getPayload(),
                        Duration.between(LocalDateTime.now(), LocalDateTime.now().plusSeconds(1)));
                delayTaskMapper.increaseRetry(task.getTaskNo());
                log.warn("delay task re-offered taskNo={} retry={}",
                        task.getTaskNo(), task.getRetryCount() + 1);
            } catch (Exception ex) {
                log.error("compensate delay task failed taskNo={}", task.getTaskNo(), ex);
            }
        }
    }

}
