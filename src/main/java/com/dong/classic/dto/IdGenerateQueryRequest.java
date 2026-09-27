package com.dong.classic.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 批量生成 id 请求。四种策略各有取舍：
 * 雪花算法趋势递增但依赖机器时钟，号段模式对数据库有压力但绝对递增，
 * INCR 最简单但会暴露业务量，UUID 无序不适合做数据库主键。
 */
public class IdGenerateQueryRequest {

    /**
     * 生成策略：snowflake、segment、incr、uuid。
     */
    @NotBlank
    @Size(max = 32)
    private String strategy = "snowflake";

    /**
     * 生成数量。
     */
    @Min(1)
    @Max(Constants.MAX_BATCH_SIZE)
    private int count = 1000;

    /**
     * 获取生成策略。
     *
     * @return 策略
     */
    public String getStrategy() {
        return strategy;
    }

    /**
     * 设置生成策略。
     *
     * @param strategy 策略
     */
    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    /**
     * 获取生成数量。
     *
     * @return 数量
     */
    public int getCount() {
        return count;
    }

    /**
     * 设置生成数量。
     *
     * @param count 数量
     */
    public void setCount(int count) {
        this.count = count;
    }

}
