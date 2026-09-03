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

    /**
     * 对账批次号。
     */
    private String batchNo;

    /**
     * 清算渠道。
     */
    private SettlementChannel channel;

    /**
     * 对账币种。
     */
    private String currency;

    /**
     * 对账执行时间。
     */
    private LocalDateTime reconTime;

    /**
     * 本地记录的总笔数。
     */
    private int localCount;

    /**
     * 本地记录的总金额。
     */
    private BigDecimal localTotal;

    /**
     * 渠道返回的总笔数。
     */
    private int channelCount;

    /**
     * 渠道返回的总金额。
     */
    private BigDecimal channelTotal;

    /**
     * 本地与渠道完全匹配的笔数。
     */
    private int matchedCount;

    /**
     * 存在差异的笔数。
     */
    private int diffCount;

    /**
     * 尚未处理的差异笔数。
     */
    private int unhandledCount;

    /**
     * 是否平衡，即总笔数与总金额是否一致且无未处理差异。
     */
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
