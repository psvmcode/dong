package com.dong.crossborder.dto;

import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

/**
 * 余额对账请求。initial 是开户时的初始余额，对账要把它算进去，
 * 否则第一笔流水之前的余额会被当成差额。
 */
public class BalanceDiffQueryRequest {

    /**
     * 开户初始余额。必须是精确小数，用浮点算金额迟早对不上账。
     */
    @Digits(integer = 16, fraction = 2)
    private BigDecimal initial = BigDecimal.ZERO;

    /**
     * 获取开户初始余额。
     *
     * @return 初始余额
     */
    public BigDecimal getInitial() {
        return initial;
    }

    /**
     * 设置开户初始余额。
     *
     * @param initial 初始余额
     */
    public void setInitial(BigDecimal initial) {
        this.initial = initial;
    }

}
