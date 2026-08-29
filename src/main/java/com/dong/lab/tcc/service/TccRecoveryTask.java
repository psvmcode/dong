package com.dong.lab.tcc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lab.tcc", name = "recovery-enabled", havingValue = "true", matchIfMissing = true)
public class TccRecoveryTask {

    private final TccCoordinatorService tccCoordinatorService;

    @Scheduled(fixedDelay = 30_000, initialDelay = 20_000)
    public void recover() {
        int recovered = tccCoordinatorService.recoverPending();
        if (recovered > 0) {
            log.info("tcc recovery finished, {} transaction(s) settled", recovered);
        }
    }

}
