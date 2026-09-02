package com.dong.lab.crossborder.dto;

import com.dong.lab.crossborder.enums.SettlementChannel;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 对账报告。一轮对账跑完后的完整结论，
 * 含本地与渠道的汇总数、差异明细与处理状态。
 */
@Data
public class ReconReportResponse {

    private String batchNo;

    private SettlementChannel channel;

    private String currency;

    private LocalDateTime reconTime;

    private int localCount;

    private BigDecimal localTotal;

    private int channelCount;

    private BigDecimal channelTotal;

    private int matchedCount;

    private int diffCount;

    private int unhandledCount;

    private boolean balanced;

    /**
     * 按差异类型分组的统计，每种类型有几条、金额合计多少。
     */
    private Map<String, Object> diffByType;

    /**
     * 差异明细列表。
     */
    private List<ReconDiffResponse> diffs;

}
