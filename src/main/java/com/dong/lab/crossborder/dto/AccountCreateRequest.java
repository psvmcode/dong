package com.dong.lab.crossborder.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 开户请求。kycLevel 决定该账户能汇出多少，
 * 这是各国监管对跨境资金的普遍要求。
 */
@Data
public class AccountCreateRequest {

    @NotBlank
    private String ownerName;

    @NotBlank
    private String country;

    @NotBlank
    private String currency;

    private BigDecimal balance;

    private BigDecimal dailyLimit;

    private BigDecimal singleLimit;

    private Integer kycLevel;

}
