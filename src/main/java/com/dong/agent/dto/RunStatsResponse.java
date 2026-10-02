package com.dong.agent.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运行统计。结束原因的分布是最有价值的一项：
 * 它能直接反映闸门是不是太紧，或者模型是不是老在同一个地方卡住。
 */
@Data
public class RunStatsResponse {

    /**
     * 运行总数
     */
    private Long total = 0L;

    /**
     * 各结束原因的数量
     */
    private Map<String, Long> finishReasonCounts = new LinkedHashMap<>();

    /**
     * 平均步数
     */
    private Double avgSteps = 0.0;

    /**
     * 平均工具调用次数
     */
    private Double avgToolCalls = 0.0;

    /**
     * 平均耗时，单位毫秒
     */
    private Double avgElapsedMillis = 0.0;

}
