package com.dong.lab.crossborder.service.impl;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import com.dong.lab.common.util.Snowflake;
import com.dong.lab.crossborder.entity.AccountLedger;
import com.dong.lab.crossborder.entity.CrossBorderAccount;
import com.dong.lab.crossborder.entity.CrossBorderRemittance;
import com.dong.lab.crossborder.enums.LedgerDirection;
import com.dong.lab.crossborder.enums.RemittanceStatus;
import com.dong.lab.crossborder.mapper.AccountLedgerMapper;
import com.dong.lab.crossborder.mapper.CrossBorderAccountMapper;
import com.dong.lab.crossborder.mapper.CrossBorderRemittanceMapper;
import com.dong.lab.crossborder.service.CrossBorderLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 账务操作实现。这里的事务能生效，是因为调用方注入的是本 bean 的代理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrossBorderLedgerServiceImpl implements CrossBorderLedgerService {

    private final CrossBorderAccountMapper accountMapper;

    private final CrossBorderRemittanceMapper remittanceMapper;

    private final AccountLedgerMapper ledgerMapper;

    private final Snowflake snowflake;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void debitAndPersist(CrossBorderRemittance remittance, CrossBorderAccount payer, BigDecimal totalDebit) {
        int deducted = accountMapper.deduct(payer.getId(), totalDebit, 0);
        if (deducted <= 0) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "insufficient balance in account " + payer.getAccountNo());
        }
        remittance.setStatus(RemittanceStatus.FUNDS_DEBITED);
        remittanceMapper.insert(remittance);
        recordLedger(remittance, payer.getId(), LedgerDirection.DEBIT, totalDebit, payer.getCurrency());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void creditAndAdvance(CrossBorderRemittance remittance) {
        accountMapper.credit(remittance.getPayeeAccountId(), remittance.getTargetAmount());
        recordLedger(remittance, remittance.getPayeeAccountId(), LedgerDirection.CREDIT,
                remittance.getTargetAmount(), remittance.getTargetCurrency());
        remittanceMapper.updateStatus(remittance.getRemittanceNo(), RemittanceStatus.SETTLED,
                RemittanceStatus.FUNDS_DEBITED, remittance.getVersion());
    }

    /**
     * 记流水。balanceAfter 用事务内重新读取的余额，保证流水能还原当时的账务快照。
     */
    private void recordLedger(CrossBorderRemittance remittance, Long accountId, LedgerDirection direction,
                              BigDecimal amount, String currency) {
        CrossBorderAccount after = accountMapper.selectById(accountId);
        AccountLedger ledger = new AccountLedger();
        ledger.setLedgerNo("LG" + snowflake.nextId());
        ledger.setRemittanceNo(remittance.getRemittanceNo());
        ledger.setAccountId(accountId);
        ledger.setDirection(direction);
        ledger.setCurrency(currency);
        ledger.setAmount(amount);
        ledger.setBalanceAfter(after.getBalance());
        ledgerMapper.insert(ledger);
    }

}
