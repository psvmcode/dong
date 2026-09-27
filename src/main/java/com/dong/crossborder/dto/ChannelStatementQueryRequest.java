package com.dong.crossborder.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * 渠道回单生成请求。差错率用来模拟渠道给错数据：
 * 0 表示回单完全准确，调大后对账应能发现漏单、金额不一致与多余单。
 */
public class ChannelStatementQueryRequest {

    /**
     * 注入的渠道差错率，取值 0 到 1。
     */
    @DecimalMin("0")
    @DecimalMax("1")
    private double errorRate = 0.0;

    /**
     * 获取渠道差错率。
     *
     * @return 差错率
     */
    public double getErrorRate() {
        return errorRate;
    }

    /**
     * 设置渠道差错率。
     *
     * @param errorRate 差错率
     */
    public void setErrorRate(double errorRate) {
        this.errorRate = errorRate;
    }

}
