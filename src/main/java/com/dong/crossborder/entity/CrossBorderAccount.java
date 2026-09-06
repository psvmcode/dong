package com.dong.crossborder.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 跨境账户。同一用户在不同币种下会有不同账户，
 * 因为跨境场景下各币种资金是分开清算的，不能混在一个余额里。
 */
@Data

public class CrossBorderAccount {

    /**
     * 主键
     */
    private Long id;

    /**
     * 账号，全局唯一
     */
    private String accountNo;

    /**
     * 户主姓名，制裁名单按此匹配
     */
    private String ownerName;

    /**
     * 所属国家或地区代码
     */
    private String country;

    /**
     * 账户币种，同一用户在不同币种下是不同账户
     */
    private String currency;

    /**
     * 账户余额
     */
    private BigDecimal balance;

    /**
     * 冻结金额，已被汇款占用的部分
     */
    private BigDecimal frozenBalance;

    /**
     * KYC 认证等级，决定可汇出额度上限
     */
    private Integer kycLevel;

    /**
     * 日累计汇出限额
     */
    private BigDecimal dailyLimit;

    /**
     * 单笔汇出限额
     */
    private BigDecimal singleLimit;

    /**
     * 账户状态，1 正常 0 冻结
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
