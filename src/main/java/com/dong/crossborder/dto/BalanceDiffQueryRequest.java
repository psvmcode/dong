package com.dong.crossborder.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 余额对账请求。initial 是开户时的初始余额，对账要把它算进去，
 * 否则第一笔流水之前的余额会被当成差额。
 */
public class BalanceDiffQueryRequest {

    /**
     * 开户初始余额。必须是精确小数，用浮点算金额迟早对不上账。
     * JSON 里显式的 null 会覆盖默认值并绕过 @Digits，
     * 因此必须 @NotNull 拦住，否则后续 add 直接 NPE。
     */
    @NotNull
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
