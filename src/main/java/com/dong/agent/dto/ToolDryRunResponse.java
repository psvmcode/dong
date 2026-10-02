package com.dong.agent.dto;

import lombok.Data;

/**
 * 工具试运行响应。失败时 errorMessage 有值，页面要把它显示出来——
 * 试运行的意义就是看清楚失败长什么样。
 */
@Data
public class ToolDryRunResponse {

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 结果内容
     */
    private String payload;

    /**
     * 失败原因
     */
    private String errorMessage;

    /**
     * 耗时，单位毫秒
     */
    private Long elapsedMillis;

}
