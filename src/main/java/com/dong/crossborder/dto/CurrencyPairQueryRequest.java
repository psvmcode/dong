package com.dong.crossborder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 货币对查询请求。查可用报价与查中间价都只依赖一对币种。
 */
public class CurrencyPairQueryRequest {

    /**
     * 源币种。
     */
    @NotBlank
    @Size(max = 128)
    private String sourceCurrency;

    /**
     * 目标币种。
     */
    @NotBlank
    @Size(max = 128)
    private String targetCurrency;

    /**
     * 获取源币种。
     *
     * @return 源币种
     */
    public String getSourceCurrency() {
        return sourceCurrency;
    }

    /**
     * 设置源币种。
     *
     * @param sourceCurrency 源币种
     */
    public void setSourceCurrency(String sourceCurrency) {
        this.sourceCurrency = sourceCurrency;
    }

    /**
     * 获取目标币种。
     *
     * @return 目标币种
     */
    public String getTargetCurrency() {
        return targetCurrency;
    }

    /**
     * 设置目标币种。
     *
     * @param targetCurrency 目标币种
     */
    public void setTargetCurrency(String targetCurrency) {
        this.targetCurrency = targetCurrency;
    }

}
