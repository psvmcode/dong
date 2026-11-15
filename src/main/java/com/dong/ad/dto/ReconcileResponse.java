package com.dong.ad.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 对账结果。以计费流水为权威源重算汇总，差异不静默吞掉，全部写进 details。
 */
@Data
public class ReconcileResponse {

    /**
     * 对账日期
     */
    private LocalDate statDate;

    /**
     * 扫描到的计费流水数
     */
    private int chargeRecords;

    /**
     * 重算的计划日汇总数
     */
    private int stats;

    /**
     * 修正过的汇总行数
     */
    private int correctedStats;

    /**
     * 补投的超时待计费事件数
     */
    private int resentEvents;

    /**
     * 重建的 Redis 预算配额数
     */
    private int rebuiltBudgets;

    /**
     * 超投金额，单位分
     */
    private long overspendCents;

    /**
     * 耗时，单位毫秒
     */
    private long elapsedMillis;

    /**
     * 差异明细
     */
    private List<String> details = List.of();

}
