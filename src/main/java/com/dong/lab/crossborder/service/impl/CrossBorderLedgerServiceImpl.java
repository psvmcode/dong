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

    /**
     * accountMapper，MyBatis Mapper 数据访问层。
     */
    private final CrossBorderAccountMapper accountMapper;

    /**
     * remittanceMapper，MyBatis Mapper 数据访问层。
     */
    private final CrossBorderRemittanceMapper remittanceMapper;

    /**
     * ledgerMapper，MyBatis Mapper 数据访问层。
     */
    private final AccountLedgerMapper ledgerMapper;

    /**
     * snowflake。
     */
    private final Snowflake snowflake;

    /**
     * debitAndPersist。
     */
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

    /**
     * 审核放行后的扣款。与 debitAndPersist 的差别只在单据已存在：
     * 扣余额、推进状态、记流水仍必须同事务，任一步失败整体回滚，
     * 保证不会出现「扣了钱但状态还停在 QUOTE_LOCKED」的半完成单。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void debitExisting(CrossBorderRemittance remittance, CrossBorderAccount payer, BigDecimal totalDebit) {
        int deducted = accountMapper.deduct(payer.getId(), totalDebit, 0);
        if (deducted <= 0) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "insufficient balance in account " + payer.getAccountNo());
        }
        int advanced = remittanceMapper.updateStatus(remittance.getRemittanceNo(), RemittanceStatus.FUNDS_DEBITED,
                RemittanceStatus.QUOTE_LOCKED, remittance.getVersion());
        if (advanced <= 0) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "remittance " + remittance.getRemittanceNo() + " is not in quote locked status");
        }
        recordLedger(remittance, payer.getId(), LedgerDirection.DEBIT, totalDebit, payer.getCurrency());
    }

    /**
     * 推进到清算中。这里刻意不接任何资金动作：清算中只表示「钱交出去了、还没确认到账」，
     * 此时收款方余额尚未变动，因此这一步失败不需要回滚任何账务。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markSettling(CrossBorderRemittance remittance) {
        CrossBorderRemittance current = remittanceMapper.selectByRemittanceNo(remittance.getRemittanceNo());
        if (current == null) {
            return false;
        }
        if (current.getStatus() == RemittanceStatus.SETTLING || current.getStatus() == RemittanceStatus.SETTLED) {
            return false;
        }
        if (current.getStatus() != RemittanceStatus.FUNDS_DEBITED) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "remittance " + remittance.getRemittanceNo() + " is not debited, status " + current.getStatus());
        }
        return remittanceMapper.updateStatus(remittance.getRemittanceNo(), RemittanceStatus.SETTLING,
                RemittanceStatus.FUNDS_DEBITED, current.getVersion()) > 0;
    }

    /**
     * 入账并结算。重新读取最新状态而不是沿用入参，
     * 因为调用方持有的实体在推进到清算中之后版本号已经过期。
     *
     * <p>状态推进失败必须抛异常让事务回滚：加钱成功却推进失败，
     * 会让单子停在清算中而钱已经进了收款方账户，重试时又加一次。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean creditAndAdvance(CrossBorderRemittance remittance) {
        CrossBorderRemittance current = remittanceMapper.selectByRemittanceNo(remittance.getRemittanceNo());
        if (current == null) {
            return false;
        }
        if (current.getStatus() == RemittanceStatus.SETTLED) {
            return false;
        }
        if (current.getStatus() != RemittanceStatus.SETTLING) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "remittance " + remittance.getRemittanceNo() + " is not settling, status " + current.getStatus());
        }
        accountMapper.credit(current.getPayeeAccountId(), current.getTargetAmount());
        recordLedger(current, current.getPayeeAccountId(), LedgerDirection.CREDIT,
                current.getTargetAmount(), current.getTargetCurrency());
        int advanced = remittanceMapper.updateStatus(current.getRemittanceNo(), RemittanceStatus.SETTLED,
                RemittanceStatus.SETTLING, current.getVersion());
        if (advanced <= 0) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "remittance " + remittance.getRemittanceNo() + " settled by another request");
        }
        return true;
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
