package com.dong.agent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具调用统计响应。
 */
@Data
public class ToolStatsResponse {

    /**
     * 参与统计的记录条数上限，超过则说明只统计了最近的一部分
     */
    private Integer scanLimit;

    /**
     * 各工具的统计行
     */
    private List<ToolStatRow> rows = new ArrayList<>();

}
