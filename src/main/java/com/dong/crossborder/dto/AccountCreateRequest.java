package com.dong.crossborder.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
/**
 * 开户请求。kycLevel 决定该账户能汇出多少，
 * 这是各国监管对跨境资金的普遍要求。
 *
 * <p>金额一律不允许为负：负余额等于凭空造出一笔负债，
 * 负限额则让"限额"这个风控手段彻底失效。
 * 小数位限制两位与 decimal(18,2) 对齐，避免落库时静默四舍五入。
 */
@Data

public class AccountCreateRequest {

    /**
     * 账户持有人姓名，KYC 与账户归属的核心识别字段。
     */
    @NotBlank
    @Size(max = 64)
    private String ownerName;

    /**
     * 账户所属国家或地区，决定适用监管规则与币种限制。
     */
    @NotBlank
    @Size(max = 8)
    private String country;

    /**
     * 账户币种，后续汇入、汇出必须按该币种进行资金归集。
     * 大写字母，非法币种在询价阶段才会暴露，不如开户时就拦下。
     */
    @NotBlank
    @Size(max = 8)
    @Pattern(regexp = "^[A-Z]+$", message = "currency must be upper case letters")
    private String currency;

    /**
     * 开户时的初始余额，仅用于注入 seed 资金，生产环境通常为零。
     */
    @DecimalMin(value = "0")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal balance;

    /**
     * 每日累计汇出限额，与单笔限额共同控制资金流出速度。
     */
    @DecimalMin(value = "0")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal dailyLimit;

    /**
     * 单笔汇出限额，防止单笔下注过大造成突发性资金损失。
     */
    @DecimalMin(value = "0")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal singleLimit;

    /**
     * KYC 等级，等级越高可享受的渠道路由限额与优惠政策越大。
     * 落库为 tinyint，超出范围会被静默截断。
     */
    @Min(0)
    @Max(3)
    private Integer kycLevel;

}
