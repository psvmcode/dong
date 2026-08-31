package com.dong.lab.crossborder.dto;

import com.dong.lab.crossborder.entity.AccountLedger;
import com.dong.lab.crossborder.enums.LedgerDirection;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LedgerResponse {

    private String ledgerNo;

    private String remittanceNo;

    private Long accountId;

    private String accountNo;

    private LedgerDirection direction;

    private String currency;

    private BigDecimal amount;

    private BigDecimal balanceAfter;

    private LocalDateTime createTime;

    public static LedgerResponse from(AccountLedger entity) {
        LedgerResponse response = new LedgerResponse();
        response.setLedgerNo(entity.getLedgerNo());
        response.setRemittanceNo(entity.getRemittanceNo());
        response.setAccountId(entity.getAccountId());
        response.setDirection(entity.getDirection());
        response.setCurrency(entity.getCurrency());
        response.setAmount(entity.getAmount());
        response.setBalanceAfter(entity.getBalanceAfter());
        response.setCreateTime(entity.getCreateTime());
        return response;
    }

    public static LedgerResponse from(AccountLedger entity, String accountNo) {
        LedgerResponse response = from(entity);
        response.setAccountNo(accountNo);
        return response;
    }

}
