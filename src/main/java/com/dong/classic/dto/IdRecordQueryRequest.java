package com.dong.classic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发号记录查询请求。按策略筛选，可用来对比不同策略的耗时。
 */
public class IdRecordQueryRequest extends LabRecordQueryRequest {

    /**
     * 发号策略：snowflake、segment、incr、uuid。
     */
    @NotBlank
    @Size(max = 32)
    private String strategy = "snowflake";

    /**
     * 获取发号策略。
     *
     * @return 策略
     */
    public String getStrategy() {
        return strategy;
    }

    /**
     * 设置发号策略。
     *
     * @param strategy 策略
     */
    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

}
