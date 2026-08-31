package com.dong.lab.crossborder.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 跨境账户。同一用户在不同币种下会有不同账户，
 * 因为跨境场景下各币种资金是分开清算的，不能混在一个余额里。
 */
@Data
public class CrossBorderAccount {

    private Long id;

    private String accountNo;

    private String ownerName;

    private String country;

    private String currency;

    private BigDecimal balance;

    private BigDecimal frozenBalance;

    private Integer kycLevel;

    private BigDecimal dailyLimit;

    private BigDecimal singleLimit;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
