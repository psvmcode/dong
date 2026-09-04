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

    /**
     * 账户持有人姓名，KYC 与账户归属的核心识别字段。
     */
    @NotBlank
    private String ownerName;

    /**
     * 账户所属国家或地区，决定适用监管规则与币种限制。
     */
    @NotBlank
    private String country;

    /**
     * 账户币种，后续汇入、汇出必须按该币种进行资金归集。
     */
    @NotBlank
    private String currency;

    /**
     * 开户时的初始余额，仅用于注入 seed 资金，生产环境通常为零。
     */
    private BigDecimal balance;

    /**
     * 每日累计汇出限额，与单笔限额共同控制资金流出速度。
     */
    private BigDecimal dailyLimit;

    /**
     * 单笔汇出限额，防止单笔下注过大造成突发性资金损失。
     */
    private BigDecimal singleLimit;

    /**
     * KYC 等级，等级越高可享受的渠道路由限额与优惠政策越大。
     */
    private Integer kycLevel;

}
