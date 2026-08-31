package com.dong.lab.crossborder.service.impl;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import com.dong.lab.common.result.PageResult;
import com.dong.lab.common.result.PageRequest;
import com.dong.lab.common.util.JsonUtils;
import com.dong.lab.common.util.Snowflake;
import com.dong.lab.crossborder.dto.RemittanceCreateRequest;
import com.dong.lab.crossborder.dto.RemittanceResponse;
import com.dong.lab.crossborder.entity.AccountLedger;
import com.dong.lab.crossborder.entity.CrossBorderAccount;
import com.dong.lab.crossborder.entity.CrossBorderRemittance;
import com.dong.lab.crossborder.enums.ComplianceResult;
import com.dong.lab.crossborder.enums.LedgerDirection;
import com.dong.lab.crossborder.enums.RemittanceStatus;
import com.dong.lab.crossborder.mapper.AccountLedgerMapper;
import com.dong.lab.crossborder.mapper.CrossBorderAccountMapper;
import com.dong.lab.crossborder.mapper.CrossBorderRemittanceMapper;
import com.dong.lab.crossborder.service.ComplianceService;
import com.dong.lab.crossborder.service.CrossBorderLedgerService;
import com.dong.lab.crossborder.service.FxQuoteService;
import com.dong.lab.crossborder.service.RemittanceService;
import com.dong.lab.framework.lock.DistributedLockService;
import com.dong.lab.framework.mq.MqFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * 跨境汇款实现。
 *
 * <p>主链路：幂等校验 → 合规筛查 → 锁汇 → 扣款记账（本地事务）→ 发送清算消息。
 * 扣款、记账、状态更新三者在同一事务内，保证资金与流水必定一致；
 * 消息在事务提交成功后才发送，避免事务回滚却已通知下游。
 *
 * <p>消息发送失败不回滚，而是留下 FUNDS_DEBITED 状态的单子，
 * 由定时任务扫描补偿。这是最终一致的标准处理：
 * 资金已经在本地扣掉，回滚才是错的，重试推进才是对的。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RemittanceServiceImpl implements RemittanceService {

    public static final String SETTLEMENT_TOPIC = "cross-border-settlement";

    private static final String LOCK_PREFIX = "lab:crossborder:idem:";

    private final CrossBorderRemittanceMapper remittanceMapper;

    private final CrossBorderAccountMapper accountMapper;

    private final AccountLedgerMapper ledgerMapper;

    private final CrossBorderLedgerService ledgerService;

    private final ComplianceService complianceService;

    private final FxQuoteService fxQuoteService;

    private final DistributedLockService distributedLockService;

    private final MqFacade mqFacade;

    private final Snowflake snowflake;

    private final LongAdder created = new LongAdder();

    private final LongAdder idempotentHit = new LongAdder();

    private final LongAdder complianceRejected = new LongAdder();

    private final LongAdder messageSent = new LongAdder();

    private final LongAdder messageFailed = new LongAdder();

    @Override
    public RemittanceResponse create(RemittanceCreateRequest request) {
        CrossBorderRemittance existing = remittanceMapper.selectByIdempotentKey(request.getIdempotentKey());
        if (existing != null) {
            idempotentHit.increment();
            log.info("idempotent replay hit key={} remittanceNo={}",
                    request.getIdempotentKey(), existing.getRemittanceNo());
            return toResponse(existing);
        }

        try (var ignored = distributedLockService.tryLock(LOCK_PREFIX + request.getIdempotentKey(),
                Duration.ofSeconds(10), Duration.ofSeconds(5))) {
            CrossBorderRemittance again = remittanceMapper.selectByIdempotentKey(request.getIdempotentKey());
            if (again != null) {
                idempotentHit.increment();
                return toResponse(again);
            }
            return doCreate(request);
        }
    }

    private RemittanceResponse doCreate(RemittanceCreateRequest request) {
        CrossBorderAccount payer = requireAccount(request.getPayerAccountNo());
        CrossBorderAccount payee = requireAccount(request.getPayeeAccountNo());
        if (payer.getCurrency().equals(payee.getCurrency())) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID,
                    "cross border remittance requires different currencies");
        }

        CrossBorderRemittance remittance = buildRemittance(request, payer);
        ComplianceResult verdict = complianceService.screen(remittance, payer, request.getSourceAmount());
        if (verdict == ComplianceResult.REJECT) {
            complianceRejected.increment();
            remittance.setStatus(RemittanceStatus.COMPLIANCE_REJECTED);
            remittance.setComplianceStatus(verdict.getCode());
            remittanceMapper.insert(remittance);
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "remittance rejected by compliance check");
        }
        if (verdict == ComplianceResult.MANUAL_REVIEW) {
            complianceRejected.increment();
            remittance.setStatus(RemittanceStatus.COMPLIANCE_REJECTED);
            remittance.setComplianceStatus(verdict.getCode());
            remittance.setFailReason("pending manual review");
            remittanceMapper.insert(remittance);
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "remittance suspended for manual review");
        }

        BigDecimal rate = fxQuoteService.lock(remittance.getQuoteNo(), remittance.getRemittanceNo());
        BigDecimal fee = fxQuoteService.fee(request.getSourceAmount(), request.getChannel());
        BigDecimal targetAmount = request.getSourceAmount().multiply(rate)
                .setScale(2, RoundingMode.HALF_UP);
        remittance.setExchangeRate(rate);
        remittance.setFeeAmount(fee);
        remittance.setTargetAmount(targetAmount);
        remittance.setComplianceStatus(ComplianceResult.PASS.getCode());
        remittance.setStatus(RemittanceStatus.QUOTE_LOCKED);
        ledgerService.debitAndPersist(remittance, payer, request.getSourceAmount().add(fee));
        fxQuoteService.markUsed(remittance.getQuoteNo());
        created.increment();
        sendSettlementMessage(remittance);
        return toResponse(remittance);
    }

    /**
     * 消息在事务提交之后发送。若当前仍在事务中，注册回调等提交成功再发，
     * 否则会出现事务回滚而消息已经发出的不一致。
     */
    private void sendSettlementMessage(CrossBorderRemittance remittance) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("remittanceNo", remittance.getRemittanceNo());
        payload.put("batchNo", remittance.getBatchNo());
        payload.put("targetAmount", remittance.getTargetAmount().toPlainString());
        payload.put("payeeAccountId", remittance.getPayeeAccountId());
        payload.put("currency", remittance.getTargetCurrency());
        payload.put("channel", remittance.getChannel().getCode());
        payload.put("occurredAt", LocalDateTime.now().toString());
        String body = JsonUtils.toJson(payload);
        Runnable task = () -> {
            try {
                mqFacade.sendOrdered(SETTLEMENT_TOPIC, remittance.getRemittanceNo(), body,
                        String.valueOf(remittance.getPayeeAccountId()));
                messageSent.increment();
            } catch (Exception ex) {
                messageFailed.increment();
                log.error("failed to send settlement message remittanceNo={}", remittance.getRemittanceNo(), ex);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            task.run();
                        }
                    });
        } else {
            task.run();
        }
    }

    @Override
    public RemittanceResponse findByRemittanceNo(String remittanceNo) {
        CrossBorderRemittance remittance = remittanceMapper.selectByRemittanceNo(remittanceNo);
        if (remittance == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND,
                    "remittance " + remittanceNo + " not found");
        }
        return toResponse(remittance);
    }

    @Override
    public RemittanceResponse findByIdempotentKey(String idempotentKey) {
        CrossBorderRemittance remittance = remittanceMapper.selectByIdempotentKey(idempotentKey);
        if (remittance == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND,
                    "no remittance for idempotent key " + idempotentKey);
        }
        return toResponse(remittance);
    }

    @Override
    public PageResult<RemittanceResponse> findByPage(RemittanceStatus status, int pageNum, int pageSize) {
        PageRequest pageRequest = PageRequest.of(pageNum, pageSize);
        List<CrossBorderRemittance> list = remittanceMapper.selectPage(status,
                pageRequest.getOffset(), pageRequest.getPageSize());
        long total = remittanceMapper.countByStatus(status);
        List<RemittanceResponse> items = list.stream().map(this::toResponse).toList();
        return PageResult.of(items, total, pageRequest);
    }

    @Override
    public List<RemittanceResponse> findByBatchNo(String batchNo) {
        return remittanceMapper.selectByBatchNo(batchNo).stream().map(this::toResponse).toList();
    }

    /**
     * 状态推进。带期望状态与版本号形成乐观锁，
     * 并发推进时只有一个成功，其余需要重读后重新决策。
     * 已处于目标状态时返回 true，保证重复消费不会出错。
     */
    @Override
    public boolean advance(String remittanceNo, RemittanceStatus expected, RemittanceStatus target) {
        CrossBorderRemittance remittance = remittanceMapper.selectByRemittanceNo(remittanceNo);
        if (remittance == null) {
            return false;
        }
        if (remittance.getStatus() == target) {
            return true;
        }
        if (remittance.getStatus() != expected) {
            log.warn("skip advance remittanceNo={} current={} expected={}", remittanceNo,
                    remittance.getStatus(), expected);
            return false;
        }
        int updated = remittanceMapper.updateStatus(remittanceNo, target, expected, remittance.getVersion());
        return updated > 0;
    }

    /**
     * 失败退款。资金已扣的按原金额退回，并记一笔反向流水，
     * 状态置为 REFUNDED 而不是回退到之前的状态，保持状态机单向。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void failAndRefund(String remittanceNo, String reason) {
        CrossBorderRemittance remittance = remittanceMapper.selectByRemittanceNo(remittanceNo);
        if (remittance == null || remittance.getStatus().isFinal()) {
            return;
        }
        List<AccountLedger> ledgers = ledgerMapper.selectByRemittanceNo(remittanceNo);
        for (AccountLedger ledger : ledgers) {
            if (ledger.getDirection() != LedgerDirection.DEBIT) {
                continue;
            }
            accountMapper.credit(ledger.getAccountId(), ledger.getAmount());
            CrossBorderAccount after = accountMapper.selectById(ledger.getAccountId());
            AccountLedger refund = new AccountLedger();
            refund.setLedgerNo("LG" + snowflake.nextId());
            refund.setRemittanceNo(remittanceNo);
            refund.setAccountId(ledger.getAccountId());
            refund.setDirection(LedgerDirection.CREDIT);
            refund.setCurrency(ledger.getCurrency());
            refund.setAmount(ledger.getAmount());
            refund.setBalanceAfter(after.getBalance());
            ledgerMapper.insert(refund);
        }
        String trimmed = reason == null ? "" : reason.substring(0, Math.min(255, reason.length()));
        remittanceMapper.updateFailReason(remittanceNo, RemittanceStatus.REFUNDED, trimmed);
    }

    @Override
    public Map<String, Object> runtime() {
        Map<String, Object> runtime = new LinkedHashMap<>();
        for (RemittanceStatus status : RemittanceStatus.values()) {
            runtime.put(status.name(), remittanceMapper.countByStatus(status));
        }
        runtime.put("created", created.sum());
        runtime.put("idempotentHit", idempotentHit.sum());
        runtime.put("complianceRejected", complianceRejected.sum());
        runtime.put("messageSent", messageSent.sum());
        runtime.put("messageFailed", messageFailed.sum());
        runtime.put("sanctionSize", complianceService.sanctionCount());
        runtime.put("mq", mqFacade.status());
        return runtime;
    }

    @Override
    public int clearAll() {
        int affected = remittanceMapper.clearAll();
        ledgerMapper.clearAll();
        return affected;
    }

    private CrossBorderRemittance buildRemittance(RemittanceCreateRequest request, CrossBorderAccount payer) {
        CrossBorderAccount payee = requireAccount(request.getPayeeAccountNo());
        CrossBorderRemittance remittance = new CrossBorderRemittance();
        remittance.setRemittanceNo("RM" + snowflake.nextId());
        remittance.setIdempotentKey(request.getIdempotentKey());
        remittance.setPayerAccountId(payer.getId());
        remittance.setPayeeAccountId(payee.getId());
        remittance.setSourceCurrency(payer.getCurrency());
        remittance.setTargetCurrency(payee.getCurrency());
        remittance.setSourceAmount(request.getSourceAmount());
        remittance.setExchangeRate(BigDecimal.ZERO);
        remittance.setTargetAmount(BigDecimal.ZERO);
        remittance.setFeeAmount(BigDecimal.ZERO);
        remittance.setChannel(request.getChannel());
        remittance.setStatus(RemittanceStatus.CREATED);
        remittance.setComplianceStatus(0);
        remittance.setQuoteNo(request.getQuoteNo() == null || request.getQuoteNo().isBlank()
                ? fxQuoteService.quote(payer.getCurrency(), payee.getCurrency(), 300L).getQuoteNo()
                : request.getQuoteNo());
        remittance.setBatchNo("");
        remittance.setFailReason("");
        remittance.setVersion(0);
        return remittance;
    }

    private CrossBorderAccount requireAccount(String accountNo) {
        CrossBorderAccount account = accountMapper.selectByAccountNo(accountNo);
        if (account == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "account " + accountNo + " not found");
        }
        return account;
    }

    private RemittanceResponse toResponse(CrossBorderRemittance remittance) {
        CrossBorderAccount payer = accountMapper.selectById(remittance.getPayerAccountId());
        CrossBorderAccount payee = accountMapper.selectById(remittance.getPayeeAccountId());
        return RemittanceResponse.from(remittance,
                payer == null ? "" : payer.getAccountNo(),
                payee == null ? "" : payee.getAccountNo());
    }

}
