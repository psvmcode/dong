package com.dong.lab.crossborder.service.impl;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import com.dong.lab.common.util.Snowflake;
import com.dong.lab.crossborder.dto.AccountCreateRequest;
import com.dong.lab.crossborder.dto.AccountResponse;
import com.dong.lab.crossborder.entity.CrossBorderAccount;
import com.dong.lab.crossborder.enums.LedgerDirection;
import com.dong.lab.crossborder.mapper.AccountLedgerMapper;
import com.dong.lab.crossborder.mapper.CrossBorderAccountMapper;
import com.dong.lab.crossborder.service.CrossBorderAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrossBorderAccountServiceImpl implements CrossBorderAccountService {

    private final CrossBorderAccountMapper accountMapper;

    private final AccountLedgerMapper ledgerMapper;

    private final Snowflake snowflake;

    @Override
    public Long create(AccountCreateRequest request) {
        CrossBorderAccount account = new CrossBorderAccount();
        account.setAccountNo("CB" + snowflake.nextId());
        account.setOwnerName(request.getOwnerName());
        account.setCountry(request.getCountry());
        account.setCurrency(request.getCurrency());
        account.setBalance(request.getBalance() == null ? BigDecimal.ZERO : request.getBalance());
        account.setFrozenBalance(BigDecimal.ZERO);
        account.setKycLevel(request.getKycLevel() == null ? 1 : request.getKycLevel());
        account.setDailyLimit(request.getDailyLimit() == null ? new BigDecimal("100000") : request.getDailyLimit());
        account.setSingleLimit(request.getSingleLimit() == null ? new BigDecimal("50000") : request.getSingleLimit());
        account.setStatus(1);
        accountMapper.insert(account);
        log.info("cross border account created accountNo={} currency={}", account.getAccountNo(), account.getCurrency());
        return account.getId();
    }

    @Override
    public AccountResponse findByAccountNo(String accountNo) {
        CrossBorderAccount account = accountMapper.selectByAccountNo(accountNo);
        if (account == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "account " + accountNo + " not found");
        }
        return AccountResponse.from(account);
    }

    @Override
    public AccountResponse findById(Long id) {
        CrossBorderAccount account = accountMapper.selectById(id);
        if (account == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "account " + id + " not found");
        }
        return AccountResponse.from(account);
    }

    @Override
    public List<AccountResponse> findAll() {
        return accountMapper.selectAll().stream().map(AccountResponse::from).toList();
    }

    /**
     * 用流水反推余额再与实际余额比对。贷方减借方的累计应等于余额变动，
     * 对不上说明有记账遗漏或重复，这是资金系统的基本自检手段。
     */
    @Override
    public BigDecimal balanceDiff(Long accountId, BigDecimal initialBalance) {
        BigDecimal credit = ledgerMapper.sumByAccountAndDirection(accountId, LedgerDirection.CREDIT);
        BigDecimal debit = ledgerMapper.sumByAccountAndDirection(accountId, LedgerDirection.DEBIT);
        CrossBorderAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal expected = initialBalance.add(credit).subtract(debit);
        return account.getBalance().subtract(expected);
    }

    @Override
    public int clearAll() {
        int affected = accountMapper.clearAll();
        ledgerMapper.clearAll();
        return affected;
    }

}
