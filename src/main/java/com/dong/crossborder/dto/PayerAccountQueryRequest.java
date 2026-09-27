package com.dong.crossborder.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 付款人维度查询请求。交易画像与日限额重置都按付款人账户定位。
 */
public class PayerAccountQueryRequest {

    /**
     * 付款人账户 id。
     */
    @NotNull
    private Long payerAccountId;

    /**
     * 获取付款人账户 id。
     *
     * @return 账户 id
     */
    public Long getPayerAccountId() {
        return payerAccountId;
    }

    /**
     * 设置付款人账户 id。
     *
     * @param payerAccountId 账户 id
     */
    public void setPayerAccountId(Long payerAccountId) {
        this.payerAccountId = payerAccountId;
    }

}
