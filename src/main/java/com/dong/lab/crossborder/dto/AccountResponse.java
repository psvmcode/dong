package com.dong.lab.crossborder.dto;

import com.dong.lab.crossborder.entity.CrossBorderAccount;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AccountResponse {

    private Long id;

    private String accountNo;

    private String ownerName;

    private String country;

    private String currency;

    private BigDecimal balance;

    private BigDecimal frozenBalance;

    private BigDecimal availableBalance;

    private Integer kycLevel;

    private BigDecimal dailyLimit;

    private BigDecimal singleLimit;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

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
