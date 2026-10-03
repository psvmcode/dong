package com.dong.agent.task;

import com.dong.agent.entity.AgentRun;
import com.dong.agent.enums.FinishReason;
import com.dong.agent.enums.RunStatus;
import com.dong.agent.mapper.AgentRunMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 卡死运行清理任务。
 *
 * <p>落库刻意不走长事务（一次运行可能持续几十秒，长事务会占满连接池），
 * 代价就是进程中途宕机会留下「半截运行」——状态停在 RUNNING，谁也不会再管它。
 * 这个任务就是给这些记录收尾的。
 *
 * <p>进程内还有一道墙钟超时兜底，两者不是重复：超时管的是「正在跑的」，
 * 清理任务管的是「跑了一半进程没了」的。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dong.agent", name = "enabled", havingValue = "true")
public class AgentRunCleanupTask {

    /**
     * runMapper，MyBatis Mapper 数据访问层。
     */
    private final AgentRunMapper runMapper;

    /**
     * 运行超过这么久仍没更新就算卡死。它比运行墙钟超时大得多：
     * 进程内的超时已经把正常慢的任务处理掉了，这里只收进程重启后的残留。
     */
    @Value("${dong.agent.lab.cleanup-stuck-after:600s}")
    private Duration stuckAfter;

    /**
     * 等待确认超过这么久按取消处理。用户点了确认才算数，
     * 挂在半路的运行不该一直占着一个等待状态。
     */
    @Value("${dong.agent.tools.side-effect-confirm-timeout:5m}")
    private Duration confirmTimeout;

    /**
     * 单轮最多处理多少条，避免一次扫太多。
     */
    @Value("${dong.agent.lab.cleanup-scan-limit:50}")
    private int scanLimit;

    /**
     * 给卡死的运行与确认超时的运行收尾。
     */
    @Scheduled(fixedDelayString = "${dong.agent.lab.cleanup-interval-ms:300000}",
            initialDelayString = "${dong.agent.lab.cleanup-initial-delay-ms:60000}")
    public void cleanup() {
        try {
            int stuck = cleanupByStatus(RunStatus.RUNNING, stuckAfter, RunStatus.FAILED,
                    FinishReason.ERROR, "run stuck, cleaned up by task");
            int expired = cleanupByStatus(RunStatus.WAITING_CONFIRM, confirmTimeout, RunStatus.CANCELLED,
                    FinishReason.CANCELLED, "confirm timeout, cancelled by task");
            if (stuck + expired > 0) {
                log.warn("agent run cleanup done stuck={} confirmExpired={}", stuck, expired);
            }
        } catch (Exception e) {
            // 调度线程会吞掉异常，不打日志就只能看到任务「静默地不再工作」
            log.error("agent run cleanup failed", e);
        }
    }

    /**
     * 把停留在某个状态超过阈值的运行收尾。
     *
     * @param status   要清理的状态
     * @param threshold 停留超过这么久
     * @param target   收尾后的状态
     * @param reason   结束原因
     * @param message  说明
     * @return 处理条数
     */
    private int cleanupByStatus(RunStatus status, Duration threshold, RunStatus target,
                                FinishReason reason, String message) {
        List<AgentRun> runs = runMapper.selectByStatusBefore(status, LocalDateTime.now().minus(threshold), scanLimit);
        for (AgentRun run : runs) {
            run.setStatus(target);
            run.setFinishReason(reason);
            run.setErrorMessage(message);
            runMapper.updateFinish(run);
        }
        return runs.size();
    }

}
