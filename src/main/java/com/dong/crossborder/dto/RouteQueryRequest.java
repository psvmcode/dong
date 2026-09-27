package com.dong.crossborder.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

/**
 * 渠道路由试算请求。金额必须是精确小数，
 * 用浮点算手续费再拿去比价，会在边界上选出错误的渠道。
 */
public class RouteQueryRequest {

    /**
     * 汇款金额。
     */
    @DecimalMin("0.01")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal amount;

    /**
     * 是否加急。加急会牺牲费率换时效。
     */
    private boolean urgent = false;

    /**
     * 获取汇款金额。
     *
     * @return 汇款金额
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * 设置汇款金额。
     *
     * @param amount 汇款金额
     */
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    /**
     * 是否加急。
     *
     * @return 是否加急
     */
    public boolean isUrgent() {
        return urgent;
    }

    /**
     * 设置是否加急。
     *
     * @param urgent 是否加急
     */
    public void setUrgent(boolean urgent) {
        this.urgent = urgent;
    }

}
