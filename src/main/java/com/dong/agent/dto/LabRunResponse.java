package com.dong.agent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 一组对照实验的结果。
 */
@Data
public class LabRunResponse {

    /**
     * 实验编号
     */
    private String experiment;

    /**
     * 实验说明
     */
    private String title;

    /**
     * 观测指标说明
     */
    private String metrics;

    /**
     * 各模式的结果
     */
    private List<LabRunItem> items = new ArrayList<>();

}
