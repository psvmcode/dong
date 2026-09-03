package com.dong.lab.crossborder.dto;

import com.dong.lab.crossborder.entity.AccountLedger;
import com.dong.lab.crossborder.enums.LedgerDirection;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账户流水响应。balanceAfter 是记账后的余额快照，
 * 把全部流水按时间排列即可还原每一步之后的余额，这是审计核对的基础。
 */
@Data
public class LedgerResponse {

    /**
     * 流水号，每条账户变动的唯一标识。
     */
    private String ledgerNo;

    /**
     * 关联的汇款单号，便于从汇款维度追踪资金轨迹。
     */
    private String remittanceNo;

    /**
     * 账户内部 id，用于关联账户表。
     */
    private Long accountId;

    /**
     * 账户编号，对外展示的资金账户标识。
     */
    private String accountNo;

    /**
     * 借贷方向，标识这笔流水是增加还是减少账户余额。
     */
    private LedgerDirection direction;

    /**
     * 流水币种。
     */
    private String currency;

    /**
     * 变动金额。
     */
    private BigDecimal amount;

    /**
     * 变动后的余额快照，按时间排列可还原账户余额变化过程。
     */
    private BigDecimal balanceAfter;

    /**
     * 流水记录时间。
     */
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
