package com.dong.ad.task;

import com.dong.ad.dto.ReconcileResponse;
import com.dong.ad.service.AdSettlementService;
import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 投放对账与补偿任务。
 *
 * <p>它承担三件线上一定会发生的事：消息丢失后补投超时 PENDING 事件、
 * 实时汇总与权威流水出现偏差时重算、Redis 配额副本漂移后按库重建。
 * 没有它，短时不一致会永久留在系统里。
 *
 * <p>方法体必须整体 try/catch：调度线程会吞掉异常，
 * 不打日志就只能看到任务「静默地不再工作」。逐日再包一层，
 * 某一天流水超限不能挡住其他日期的对账。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dong.ad", name = "reconcile-enabled", havingValue = "true", matchIfMissing = true)
public class AdReconciliationTask {

    /**
     * adSettlementService，业务服务层。
     */
    private final AdSettlementService adSettlementService;

    /**
     * 回溯天数，跨日补偿要让昨天的流水也能被重算。
     */
    @Value("${dong.ad.reconcile-lookback-days:2}")
    private int lookbackDays;

    /**
     * 周期性对账。
     */
    @Scheduled(fixedDelayString = "${dong.ad.reconcile-interval-ms:300000}",
            initialDelayString = "${dong.ad.reconcile-initial-delay-ms:60000}")
    public void reconcile() {
        try {
            int days = Math.min(Math.max(1, lookbackDays), Constants.MAX_REPORT_DAYS);
            for (int offset = 0; offset < days; offset++) {
                LocalDate date = LocalDate.now().minusDays(offset);
                try {
                    ReconcileResponse response = adSettlementService.reconcile(date, true);
                    if (response.getCorrectedStats() > 0 || response.getResentEvents() > 0 || response.getOverspendCents() > 0L) {
                        log.warn("ad reconcile date={} corrected={} resent={} overspend={}", date,
                                response.getCorrectedStats(), response.getResentEvents(), response.getOverspendCents());
                    }
                } catch (BusinessException ex) {
                    // 1007 是容量保护，截断对账会得出错误结论，跳过这一天比算错好
                    log.warn("ad reconcile skipped date={} code={} message={}", date, ex.getCode(), ex.getMessage());
                } catch (Exception ex) {
                    log.error("ad reconcile failed date={}", date, ex);
                }
            }
        } catch (Exception ex) {
            log.error("ad reconciliation task failed", ex);
        }
    }

}
