package com.dong.lab.crossborder.dto;

import com.dong.lab.crossborder.entity.CrossBorderAccount;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 账户响应。availableBalance 是真正可动用的钱：余额减去冻结部分。
 * 只看 balance 会误判可汇额度，这是运营侧最常见的口径误解。
 */
@Data

public class AccountResponse {

    /**
     * 账户内部唯一 id，不对外展示。
     */
    private Long id;

    /**
     * 账户编号，对外展示的唯一标识。
     */
    private String accountNo;

    /**
     * 账户持有人姓名。
     */
    private String ownerName;

    /**
     * 账户所属国家或地区。
     */
    private String country;

    /**
     * 账户币种。
     */
    private String currency;

    /**
     * 账户总余额，包含被冻结部分。
     */
    private BigDecimal balance;

    /**
     * 冻结金额，争议、复核或监管要求期间不可动用。
     */
    private BigDecimal frozenBalance;

    /**
     * 可用余额，由总余额减去冻结金额计算得出。
     */
    private BigDecimal availableBalance;

    /**
     * KYC 等级，决定可享受的限额与渠道。
     */
    private Integer kycLevel;

    /**
     * 每日累计汇出限额。
     */
    private BigDecimal dailyLimit;

    /**
     * 单笔汇出限额。
     */
    private BigDecimal singleLimit;

    /**
     * 账户状态，例如正常、冻结、销户等。
     */
    private Integer status;

    /**
     * 账户创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 账户最后更新时间。
     */
    private LocalDateTime updateTime;

    /**
     * 从实体转换为 DTO。
     */
    public static AccountResponse from(CrossBorderAccount entity) {
        AccountResponse response = new AccountResponse();
        response.setId(entity.getId());
        response.setAccountNo(entity.getAccountNo());
        response.setOwnerName(entity.getOwnerName());
        response.setCountry(entity.getCountry());
        response.setCurrency(entity.getCurrency());
        response.setBalance(entity.getBalance());
        response.setFrozenBalance(entity.getFrozenBalance());
        response.setAvailableBalance(entity.getBalance().subtract(entity.getFrozenBalance()));
        response.setKycLevel(entity.getKycLevel());
        response.setDailyLimit(entity.getDailyLimit());
        response.setSingleLimit(entity.getSingleLimit());
        response.setStatus(entity.getStatus());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }

}
