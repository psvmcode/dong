package com.dong.lab.tcc.task;

import com.dong.lab.tcc.service.TccCoordinatorService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
/**
 * 悬挂事务恢复。进程在 Try 与 Confirm 之间宕机时，
 * 事务会停留在中间状态，冻结的资源永远不会被释放，必须由定时任务兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lab.tcc", name = "recovery-enabled", havingValue = "true", matchIfMissing = true)

public class TccRecoveryTask {

    /**
     * tccCoordinatorService，业务服务层。
     */
    private final TccCoordinatorService tccCoordinatorService;

    /**
     * 定时恢复停留在中间状态的事务。
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 20_000)
    public void recover() {
        int recovered = tccCoordinatorService.recoverPending();
        if (recovered > 0) {
            log.info("tcc recovery finished, {} transaction(s) settled", recovered);
        }
    }

}
