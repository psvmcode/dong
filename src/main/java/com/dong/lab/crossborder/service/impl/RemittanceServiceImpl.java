package com.dong.lab.crossborder.service.impl;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import com.dong.lab.common.result.PageResult;
import com.dong.lab.common.result.PageRequest;
import com.dong.lab.common.util.JsonUtils;
import com.dong.lab.common.util.Snowflake;
import com.dong.lab.crossborder.dto.RemittanceCreateRequest;
import com.dong.lab.crossborder.dto.RemittanceResponse;
import com.dong.lab.crossborder.dto.ReviewDecisionRequest;
import com.dong.lab.crossborder.entity.AccountLedger;
import com.dong.lab.crossborder.entity.CrossBorderAccount;
import com.dong.lab.crossborder.entity.CrossBorderRemittance;
import com.dong.lab.crossborder.enums.ComplianceResult;
import com.dong.lab.crossborder.enums.LedgerDirection;
import com.dong.lab.crossborder.enums.RemittanceStatus;
import com.dong.lab.crossborder.enums.SettlementChannel;
import com.dong.lab.crossborder.handler.CrossBorderSettlementHandler;
import com.dong.lab.crossborder.mapper.AccountLedgerMapper;
import com.dong.lab.crossborder.mapper.CrossBorderAccountMapper;
import com.dong.lab.crossborder.mapper.CrossBorderRemittanceMapper;
import com.dong.lab.crossborder.service.AmlMonitor;
import com.dong.lab.crossborder.service.ChannelRouter;
import com.dong.lab.crossborder.service.ComplianceService;
import com.dong.lab.crossborder.service.CrossBorderLedgerService;
import com.dong.lab.crossborder.service.FxQuoteService;
import com.dong.lab.crossborder.service.RemittanceService;
import com.dong.lab.framework.lock.DistributedLockService;
import com.dong.lab.framework.lock.LockHandle;
import com.dong.lab.framework.mq.MqFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /**
     * 账户可用状态，与 cross_border_account.status 的取值一致。
     */
    private static final int ACCOUNT_STATUS_ACTIVE = 1;


    /**
     * remittanceMapper，MyBatis Mapper 数据访问层。
     */
    private final CrossBorderRemittanceMapper remittanceMapper;

    /**
     * accountMapper，MyBatis Mapper 数据访问层。
     */
    private final CrossBorderAccountMapper accountMapper;

    /**
     * ledgerMapper，MyBatis Mapper 数据访问层。
     */
    private final AccountLedgerMapper ledgerMapper;

    /**
     * ledgerService，业务服务层。
     */
    private final CrossBorderLedgerService ledgerService;

    /**
     * complianceService，业务服务层。
     */
    private final ComplianceService complianceService;

    /**
     * fxQuoteService，业务服务层。
     */
    private final FxQuoteService fxQuoteService;

    /**
     * distributedLockService，业务服务层。
     */
    private final DistributedLockService distributedLockService;

    /**
     * channelRouter。
     */
    private final ChannelRouter channelRouter;

    /**
     * amlMonitor。
     */
    private final AmlMonitor amlMonitor;

    /**
     * mqFacade。
     */
    private final MqFacade mqFacade;

    /**
     * settlementHandler。只为读取清算计数器：
     * 「清算中」停留时间是毫秒级，按状态统计永远看不到，累计计数才观察得到。
     */
    private final CrossBorderSettlementHandler settlementHandler;

    /**
     * snowflake。
     */
    private final Snowflake snowflake;

    private final LongAdder created = new LongAdder();

    private final LongAdder idempotentHit = new LongAdder();

    private final LongAdder complianceRejected = new LongAdder();

    private final LongAdder pendingReview = new LongAdder();

    private final LongAdder messageSent = new LongAdder();

    private final LongAdder messageFailed = new LongAdder();

    private final LongAdder reviewApproved = new LongAdder();

    private final LongAdder reviewRejected = new LongAdder();

    /**
     * 创建记录。
     */
    @Override
    public RemittanceResponse create(RemittanceCreateRequest request) {
        CrossBorderRemittance existing = remittanceMapper.selectByIdempotentKey(request.getIdempotentKey());
        if (existing != null) {
            idempotentHit.increment();
            log.info("idempotent replay hit key={} remittanceNo={}",
                    request.getIdempotentKey(), existing.getRemittanceNo());
            return toResponse(existing);
        }

        try (LockHandle handle = distributedLockService.tryLock(LOCK_PREFIX + request.getIdempotentKey(),
                Duration.ofSeconds(10), Duration.ofSeconds(5))) {
            if (!handle.isAcquired()) {
                throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                        "another request with the same idempotent key is in progress");
            }
            CrossBorderRemittance again = remittanceMapper.selectByIdempotentKey(request.getIdempotentKey());
            if (again != null) {
                idempotentHit.increment();
                return toResponse(again);
            }
            try {
                return doCreate(request);
            } catch (DuplicateKeyException ex) {
                return onDuplicateIdempotentKey(request.getIdempotentKey());
            }
        }
    }

    /**
     * doCreate。
     */
    private RemittanceResponse doCreate(RemittanceCreateRequest request) {
        CrossBorderAccount payer = requireAccount(request.getPayerAccountNo());
        CrossBorderAccount payee = requireAccount(request.getPayeeAccountNo());
        if (payer.getCurrency().equals(payee.getCurrency())) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID,
                    "cross border remittance requires different currencies");
        }

        SettlementChannel channel = resolveChannel(request);
        CrossBorderRemittance remittance = buildRemittance(request, payer, payee, channel);
        ComplianceResult verdict = complianceService.screen(remittance, payer, request.getSourceAmount());
        if (verdict == ComplianceResult.REJECT) {
            // 四道检查里日限额是「先累加再判断」，被拒绝的汇款若不释放占用，
            // 客户失败几次之后当天就一笔都汇不出去了，这是真实系统里最常见的额度泄漏事故
            complianceService.releaseDailyLimit(payer.getId(), request.getSourceAmount());
            complianceRejected.increment();
            remittance.setStatus(RemittanceStatus.COMPLIANCE_REJECTED);
            remittance.setComplianceStatus(verdict.getCode());
            remittanceMapper.insert(remittance);
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "remittance rejected by compliance check");
        }
        if (verdict == ComplianceResult.MANUAL_REVIEW) {
            // 挂起期间日限额占用保留：单子还活着，额度就该被占着。
            // 审核放行沿用这份占用，驳回时由 rejectReview 释放
            pendingReview.increment();
            remittance.setStatus(RemittanceStatus.PENDING_REVIEW);
            remittance.setComplianceStatus(verdict.getCode());
            remittance.setFailReason("pending manual review");
            remittanceMapper.insert(remittance);
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "remittance suspended for manual review");
        }

        // 拆分交易检测放在常规合规之后：单独看每笔都合规，要看行为模式才能发现
        Optional<String> structuring = amlMonitor.detectStructuring(payer.getId(), request.getSourceAmount());
        structuring.ifPresent(detail -> log.warn("aml structuring signal remittanceNo={} {}", remittance.getRemittanceNo(), detail));
        BigDecimal rate = fxQuoteService.lock(remittance.getQuoteNo(), remittance.getRemittanceNo());
        BigDecimal fee = fxQuoteService.fee(request.getSourceAmount(), channel);
        BigDecimal targetAmount = request.getSourceAmount().multiply(rate)
                .setScale(2, RoundingMode.HALF_UP);
        remittance.setExchangeRate(rate);
        remittance.setFeeAmount(fee);
        remittance.setTargetAmount(targetAmount);
        remittance.setComplianceStatus(ComplianceResult.PASS.getCode());
        remittance.setStatus(RemittanceStatus.QUOTE_LOCKED);
        try {
            ledgerService.debitAndPersist(remittance, payer, request.getSourceAmount().add(fee));
        } catch (RuntimeException ex) {
            complianceService.releaseDailyLimit(payer.getId(), request.getSourceAmount());
            throw ex;
        }
        fxQuoteService.markUsed(remittance.getQuoteNo());
        created.increment();
        sendSettlementMessage(remittance);
        return toResponse(remittance);
    }

    /**
     * 唯一索引冲突说明同一幂等键已被并发请求落库，此时应返回那笔已存在的单子，
     * 而不是向上抛错。否则客户端重试会收到冲突错误，误以为汇款失败再次发起。
     */
    private RemittanceResponse onDuplicateIdempotentKey(String idempotentKey) {
        idempotentHit.increment();
        CrossBorderRemittance persisted = remittanceMapper.selectByIdempotentKey(idempotentKey);
        if (persisted == null) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "remittance for idempotent key " + idempotentKey + " is being created");
        }
        log.info("idempotent key conflict resolved by returning existing order key={} remittanceNo={}",
                idempotentKey, persisted.getRemittanceNo());
        return toResponse(persisted);
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

    /**
     * findByRemittanceNo。
     */
    @Override
    public RemittanceResponse findByRemittanceNo(String remittanceNo) {
        CrossBorderRemittance remittance = remittanceMapper.selectByRemittanceNo(remittanceNo);
        if (remittance == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND,
                    "remittance " + remittanceNo + " not found");
        }
        return toResponse(remittance);
    }

    /**
     * findByIdempotentKey。
     */
    @Override
    public RemittanceResponse findByIdempotentKey(String idempotentKey) {
        CrossBorderRemittance remittance = remittanceMapper.selectByIdempotentKey(idempotentKey);
        if (remittance == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND,
                    "no remittance for idempotent key " + idempotentKey);
        }
        return toResponse(remittance);
    }

    /**
     * 分页查询。
     */
    @Override
    public PageResult<RemittanceResponse> findByPage(RemittanceStatus status, int pageNum, int pageSize) {
        PageRequest pageRequest = PageRequest.of(pageNum, pageSize);
        List<CrossBorderRemittance> list = remittanceMapper.selectPage(status,
                pageRequest.getOffset(), pageRequest.getPageSize());
        long total = remittanceMapper.countByStatus(status);
        return PageResult.of(toResponses(list), total, pageRequest);
    }

    /**
     * findByBatchNo。
     */
    @Override
    public List<RemittanceResponse> findByBatchNo(String batchNo) {
        return toResponses(remittanceMapper.selectByBatchNo(batchNo));
    }

    /**
     * 批量转换。先一次性取回涉及的账户，再在内存里拼接，
     * 避免每笔汇款都去查两次账户造成的 N+1。
     */
    private List<RemittanceResponse> toResponses(List<CrossBorderRemittance> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = new HashSet<>();
        for (CrossBorderRemittance item : list) {
            ids.add(item.getPayerAccountId());
            ids.add(item.getPayeeAccountId());
        }
        Map<Long, String> accountNos = new HashMap<>();
        for (CrossBorderAccount account : accountMapper.selectByIds(ids)) {
            accountNos.put(account.getId(), account.getAccountNo());
        }
        return list.stream()
                .map(item -> RemittanceResponse.from(item,
                        accountNos.getOrDefault(item.getPayerAccountId(), ""),
                        accountNos.getOrDefault(item.getPayeeAccountId(), "")))
                .toList();
    }

    /**
     * 审核放行。大额汇款挂起时资金尚未扣减，放行相当于把主链路的后半段补完：
     * 锁汇 → 计算费用 → 回填成交要素 → 扣款记账 → 发送清算消息。
     *
     * <p>并发防护分两层：先用乐观锁把状态从 PENDING_REVIEW 抢占为 QUOTE_LOCKED，
     * 两个审核员同时点放行只有一个能抢到；抢占成功后的写入无并发竞争，
     * 因为失败方已经报冲突退出。锁汇或扣款中途失败会把状态回退到 PENDING_REVIEW，
     * 单子可以重新审核，不会卡死在中间状态。
     *
     * <p>审核期间付款账户可能已被冻结（反洗钱调查的常见时序），
     * 放行前必须重新校验账户可用性，不能信任挂起时的检查结果。
     */
    @Override
    public RemittanceResponse approveReview(String remittanceNo, ReviewDecisionRequest decision) {
        CrossBorderRemittance suspended = requireRemittance(remittanceNo);
        if (!suspended.getStatus().isPendingReview()) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "remittance " + remittanceNo + " is not pending review");
        }
        CrossBorderAccount payer = requireAccountById(suspended.getPayerAccountId());
        int claimed = remittanceMapper.updateStatus(remittanceNo, RemittanceStatus.QUOTE_LOCKED,
                RemittanceStatus.PENDING_REVIEW, suspended.getVersion());
        if (claimed <= 0) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "remittance " + remittanceNo + " has been handled by another reviewer");
        }
        try {
            settleSuspended(suspended, payer);
        } catch (RuntimeException ex) {
            revertToPendingReview(remittanceNo, suspended.getVersion() + 1);
            throw ex;
        }
        complianceService.recordManualDecision(remittanceNo, ComplianceResult.PASS,
                "approved by " + decision.getReviewer() + appendNote(decision.getNote()));
        reviewApproved.increment();
        created.increment();
        CrossBorderRemittance settled = remittanceMapper.selectByRemittanceNo(remittanceNo);
        sendSettlementMessage(settled);
        return toResponse(settled);
    }

    /**
     * 补完挂起单的成交与扣款。挂起时汇率、费用、目标金额还是占位 0，
     * 这里按放行时刻的市场报价重新锁定并回填，之后走与正常链路相同的扣款事务。
     */
    private void settleSuspended(CrossBorderRemittance suspended, CrossBorderAccount payer) {
        BigDecimal rate = fxQuoteService.lock(suspended.getQuoteNo(), suspended.getRemittanceNo());
        BigDecimal fee = fxQuoteService.fee(suspended.getSourceAmount(), suspended.getChannel());
        BigDecimal targetAmount = suspended.getSourceAmount().multiply(rate)
                .setScale(2, RoundingMode.HALF_UP);
        remittanceMapper.updateSettlementTerms(suspended.getRemittanceNo(), rate, fee, targetAmount,
                ComplianceResult.PASS.getCode());
        CrossBorderRemittance current = remittanceMapper.selectByRemittanceNo(suspended.getRemittanceNo());
        ledgerService.debitExisting(current, payer, suspended.getSourceAmount().add(fee));
        fxQuoteService.markUsed(suspended.getQuoteNo());
    }

    /**
     * 放行中途失败的回退。回到 PENDING_REVIEW 而不是直接失败：
     * 失败原因多是报价过期或余额不足，补齐后重新审核即可，直接判死会造成误伤。
     */
    private void revertToPendingReview(String remittanceNo, int versionAfterClaim) {
        try {
            remittanceMapper.updateStatus(remittanceNo, RemittanceStatus.PENDING_REVIEW,
                    RemittanceStatus.QUOTE_LOCKED, versionAfterClaim);
        } catch (Exception revertEx) {
            log.error("failed to revert remittance to pending review remittanceNo={}", remittanceNo, revertEx);
        }
    }

    /**
     * 审核驳回。挂起发生在扣款之前，资金从未划出，因此没有退款动作，
     * 只需推进到终态、释放挂起时占用的日限额、留下驳回记录。
     * 释放日限额与驳回同样重要：不释放的话，被驳回的金额会永久占用客户当天额度。
     */
    @Override
    public RemittanceResponse rejectReview(String remittanceNo, ReviewDecisionRequest decision) {
        CrossBorderRemittance suspended = requireRemittance(remittanceNo);
        if (!suspended.getStatus().isPendingReview()) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "remittance " + remittanceNo + " is not pending review");
        }
        int claimed = remittanceMapper.updateStatus(remittanceNo, RemittanceStatus.COMPLIANCE_REJECTED,
                RemittanceStatus.PENDING_REVIEW, suspended.getVersion());
        if (claimed <= 0) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "remittance " + remittanceNo + " has been handled by another reviewer");
        }
        complianceService.releaseDailyLimit(suspended.getPayerAccountId(), suspended.getSourceAmount());
        complianceService.recordManualDecision(remittanceNo, ComplianceResult.REJECT,
                "rejected by " + decision.getReviewer() + appendNote(decision.getNote()));
        String reason = "rejected by " + decision.getReviewer()
                + (decision.getNote() == null || decision.getNote().isBlank() ? "" : ": " + decision.getNote());
        remittanceMapper.updateFailReason(remittanceNo, RemittanceStatus.COMPLIANCE_REJECTED,
                reason.substring(0, Math.min(255, reason.length())));
        reviewRejected.increment();
        log.warn("remittance rejected by manual review remittanceNo={} reviewer={}",
                remittanceNo, decision.getReviewer());
        return findByRemittanceNo(remittanceNo);
    }

    /**
     * appendNote。
     */
    private String appendNote(String note) {
        return note == null || note.isBlank() ? "" : ", note: " + note;
    }

    /**
     * requireRemittance。
     */
    private CrossBorderRemittance requireRemittance(String remittanceNo) {
        CrossBorderRemittance remittance = remittanceMapper.selectByRemittanceNo(remittanceNo);
        if (remittance == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND,
                    "remittance " + remittanceNo + " not found");
        }
        return remittance;
    }

    /**
     * 按 id 取账户并校验可用性。审核放行时用：挂起到放行之间可能隔着数小时，
     * 期间账户可能因反洗钱调查被冻结，必须用当前状态重新判断。
     */
    private CrossBorderAccount requireAccountById(Long accountId) {
        CrossBorderAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "account " + accountId + " not found");
        }
        if (account.getStatus() == null || account.getStatus() != ACCOUNT_STATUS_ACTIVE) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "account " + account.getAccountNo() + " is not active, status " + account.getStatus());
        }
        return account;
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
     * 失败退款。先用乐观锁把状态抢占为 REFUNDED，抢占成功的线程才执行退款，
     * 并发调用只有一人真正退款，其余直接返回，不会出现重复退款。
     * 资金未扣的单子没有借方流水，循环自然跳过，仅推进状态。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void failAndRefund(String remittanceNo, String reason) {
        CrossBorderRemittance remittance = remittanceMapper.selectByRemittanceNo(remittanceNo);
        if (remittance == null || remittance.getStatus().isFinal()) {
            return;
        }
        int claimed = remittanceMapper.updateStatus(remittanceNo, RemittanceStatus.REFUNDED,
                remittance.getStatus(), remittance.getVersion());
        if (claimed <= 0) {
            log.info("refund claimed by another request remittanceNo={}", remittanceNo);
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

    /**
     * runtime。
     */
    @Override
    public Map<String, Object> runtime() {
        Map<String, Object> runtime = new LinkedHashMap<>();
        Map<Integer, Long> grouped = new HashMap<>();
        for (Map<String, Object> row : remittanceMapper.countGroupByStatus()) {
            grouped.put(((Number) row.get("status")).intValue(), ((Number) row.get("total")).longValue());
        }
        for (RemittanceStatus status : RemittanceStatus.values()) {
            runtime.put(status.name(), grouped.getOrDefault(status.getCode(), 0L));
        }
        runtime.put("created", created.sum());
        runtime.put("idempotentHit", idempotentHit.sum());
        runtime.put("complianceRejected", complianceRejected.sum());
        runtime.put("pendingReview", pendingReview.sum());
        runtime.put("reviewApproved", reviewApproved.sum());
        runtime.put("reviewRejected", reviewRejected.sum());
        runtime.put("messageSent", messageSent.sum());
        runtime.put("messageFailed", messageFailed.sum());
        runtime.put("settlingEntered", settlementHandler.settlingCount());
        runtime.put("sanctionSize", complianceService.sanctionCount());
        runtime.put("mq", mqFacade.status());
        return runtime;
    }

    /**
     * 清空全部数据，仅测试场景使用。
     */
    @Override
    public int clearAll() {
        int affected = remittanceMapper.clearAll();
        ledgerMapper.clearAll();
        return affected;
    }

    /**
     * buildRemittance。
     */
    private CrossBorderRemittance buildRemittance(RemittanceCreateRequest request, CrossBorderAccount payer,
                                                  CrossBorderAccount payee, SettlementChannel channel) {
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
        remittance.setChannel(channel);
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

    /**
     * 渠道解析。调用方指定了就尊重指定，否则交给路由器按成本与时效选择。
     * 指定的渠道若超单笔上限仍会走路由兜底，不能因偏好导致汇款失败。
     */
    private SettlementChannel resolveChannel(RemittanceCreateRequest request) {
        if (request.getChannel() != null) {
            return request.getChannel();
        }
        boolean urgent = Boolean.TRUE.equals(request.getUrgent());
        ChannelRouter.RouteDecision decision = channelRouter.route(request.getSourceAmount(), urgent);
        log.info("channel routed amount={} urgent={} channel={} reasons={}",
                request.getSourceAmount(), urgent, decision.channel(), decision.reasons());
        return decision.channel();
    }

    /**
     * 取账户并校验可用性。冻结或注销的账户不能参与汇款，
     * 真实系统里这是反洗钱与司法冻结的硬性要求。
     */
    private CrossBorderAccount requireAccount(String accountNo) {
        CrossBorderAccount account = accountMapper.selectByAccountNo(accountNo);
        if (account == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "account " + accountNo + " not found");
        }
        if (account.getStatus() == null || account.getStatus() != ACCOUNT_STATUS_ACTIVE) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "account " + accountNo + " is not active, status " + account.getStatus());
        }
        return account;
    }

    /**
     * toResponse。
     */
    private RemittanceResponse toResponse(CrossBorderRemittance remittance) {
        CrossBorderAccount payer = accountMapper.selectById(remittance.getPayerAccountId());
        CrossBorderAccount payee = accountMapper.selectById(remittance.getPayeeAccountId());
        return RemittanceResponse.from(remittance,
                payer == null ? "" : payer.getAccountNo(),
                payee == null ? "" : payee.getAccountNo());
    }

}
