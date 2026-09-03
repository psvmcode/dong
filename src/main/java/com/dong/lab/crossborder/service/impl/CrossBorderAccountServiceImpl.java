package com.dong.lab.crossborder.service.impl;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import com.dong.lab.common.util.Snowflake;
import com.dong.lab.crossborder.dto.AccountCreateRequest;
import com.dong.lab.crossborder.dto.AccountEventResponse;
import com.dong.lab.crossborder.dto.AccountResponse;
import com.dong.lab.crossborder.entity.AccountEvent;
import com.dong.lab.crossborder.entity.CrossBorderAccount;
import com.dong.lab.crossborder.enums.AccountEventType;
import com.dong.lab.crossborder.enums.AccountStatus;
import com.dong.lab.crossborder.enums.LedgerDirection;
import com.dong.lab.crossborder.mapper.AccountEventMapper;
import com.dong.lab.crossborder.mapper.AccountLedgerMapper;
import com.dong.lab.crossborder.mapper.CrossBorderAccountMapper;
import com.dong.lab.crossborder.service.CrossBorderAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 跨境账户实现。账户本身只有状态字段，冻结/解冻的历史
 * 全部落在事件表里：状态回答「现在能不能用」，事件回答「怎么变成这样的」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrossBorderAccountServiceImpl implements CrossBorderAccountService {

    private final CrossBorderAccountMapper accountMapper;

    private final AccountLedgerMapper ledgerMapper;

    private final AccountEventMapper eventMapper;

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
        account.setStatus(AccountStatus.ACTIVE.getCode());
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
     * 冻结账户。条件更新保证幂等语义：只有「当前是激活状态」的账户能被冻结，
     * 重复冻结会返回 0 并向上报冲突，避免并发操作把事件记录写重。
     * 状态变更与事件落库在同一事务，不会出现「冻了账户却没留痕」。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AccountResponse freeze(String accountNo, String reason, String operator) {
        CrossBorderAccount account = requireAccount(accountNo);
        int updated = accountMapper.updateStatus(account.getId(), AccountStatus.FROZEN.getCode(),
                AccountStatus.ACTIVE.getCode());
        if (updated <= 0) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "account " + accountNo + " is not active, freeze skipped");
        }
        recordEvent(accountNo, AccountEventType.FREEZE, reason, operator);
        log.warn("cross border account frozen accountNo={} operator={} reason={}", accountNo, operator, reason);
        return findByAccountNo(accountNo);
    }

    /**
     * 解冻账户。与冻结完全对称：只有「当前是冻结状态」的账户能被解冻。
     * 解冻同样必须留痕，否则监管无法确认账户恢复使用经过了审批。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AccountResponse unfreeze(String accountNo, String reason, String operator) {
        CrossBorderAccount account = requireAccount(accountNo);
        int updated = accountMapper.updateStatus(account.getId(), AccountStatus.ACTIVE.getCode(),
                AccountStatus.FROZEN.getCode());
        if (updated <= 0) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "account " + accountNo + " is not frozen, unfreeze skipped");
        }
        recordEvent(accountNo, AccountEventType.UNFREEZE, reason, operator);
        log.info("cross border account unfrozen accountNo={} operator={}", accountNo, operator);
        return findByAccountNo(accountNo);
    }

    @Override
    public List<AccountEventResponse> events(String accountNo) {
        requireAccount(accountNo);
        return eventMapper.selectByAccountNo(accountNo).stream()
                .map(AccountEventResponse::from)
                .toList();
    }

    /**
     * 落事件。reason 截断到表字段长度内，避免超长文本导致插入失败
     * 反而让冻结操作整体回滚——留痕失败比留痕不完整更危险。
     */
    private void recordEvent(String accountNo, AccountEventType type, String reason, String operator) {
        AccountEvent event = new AccountEvent();
        event.setAccountNo(accountNo);
        event.setEventType(type);
        event.setReason(reason == null ? "" : reason.substring(0, Math.min(255, reason.length())));
        event.setOperator(operator == null ? "" : operator.substring(0, Math.min(64, operator.length())));
        eventMapper.insert(event);
    }

    private CrossBorderAccount requireAccount(String accountNo) {
        CrossBorderAccount account = accountMapper.selectByAccountNo(accountNo);
        if (account == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "account " + accountNo + " not found");
        }
        return account;
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
        eventMapper.clearAll();
        return affected;
    }

}
