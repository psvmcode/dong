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

    /** 内部主键，对外一律用 accountNo */
    private Long id;

    /** 对外展示的账号，开户时由发号器生成 */
    private String accountNo;

    /** 账户持有人姓名，制裁名单按此匹配 */
    private String ownerName;

    /** 持有人所在国家或地区，合规筛查的维度之一 */
    private String country;

    /** 账户币种，一个币种一个账户 */
    private String currency;

    /** 账面余额，含冻结部分 */
    private BigDecimal balance;

    /** 冻结金额，可用余额 = balance - frozenBalance */
    private BigDecimal frozenBalance;

    /** 实名认证等级，决定单笔可汇上限 */
    private Integer kycLevel;

    /** 日累计汇款限额 */
    private BigDecimal dailyLimit;

    /** 单笔汇款限额 */
    private BigDecimal singleLimit;

    /** 账户状态，1 激活 2 冻结，冻结后不能发起新汇款 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
