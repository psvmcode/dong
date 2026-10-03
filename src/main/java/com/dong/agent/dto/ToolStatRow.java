package com.dong.agent.dto;

import lombok.Data;

/**
 * 按工具维度的调用统计。失败率是最该看的一项：
 * 某个工具一直失败，说明它的描述、入参或依赖出了问题，模型却在反复重试。
 */
@Data
public class ToolStatRow {

    /**
     * 工具名
     */
    private String toolName;

    /**
     * 调用次数
     */
    private Long calls;

    /**
     * 失败次数
     */
    private Long failures;

    /**
     * 平均耗时，单位毫秒
     */
    private Long avgElapsedMillis;

}
