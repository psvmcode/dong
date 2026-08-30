package com.dong.lab.tcc.service.impl;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import com.dong.lab.common.util.JsonUtils;
import com.dong.lab.common.util.Snowflake;
import com.dong.lab.tcc.dto.TccOrderRequest;
import com.dong.lab.tcc.dto.TccResultResponse;
import com.dong.lab.tcc.entity.TccAccount;
import com.dong.lab.tcc.entity.TccBranch;
import com.dong.lab.tcc.entity.TccInventory;
import com.dong.lab.tcc.entity.TccOrder;
import com.dong.lab.tcc.entity.TccTransaction;
import com.dong.lab.tcc.enums.TccBranchStatus;
import com.dong.lab.tcc.enums.TccOrderStatus;
import com.dong.lab.tcc.enums.TccTransactionStatus;
import com.dong.lab.tcc.mapper.TccParticipantMapper;
import com.dong.lab.tcc.mapper.TccTransactionMapper;
import com.dong.lab.tcc.service.TccCoordinatorService;
import com.dong.lab.tcc.service.TccParticipant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TccCoordinatorServiceImpl implements TccCoordinatorService {

    private static final String ORDER_NO_PREFIX = "TC";

    private static final int MAX_RETRY = 5;

    private static final long UNIT_PRICE = 1000L;

    private final TccTransactionMapper tccTransactionMapper;

    private final TccParticipantMapper tccParticipantMapper;

    private final List<TccParticipant> participants;

    private final Snowflake snowflake;

    @Override
    public TccResultResponse submit(TccOrderRequest request) {
        String xid = String.valueOf(snowflake.nextId());
        String orderNo = ORDER_NO_PREFIX + xid;
        long amount = request.getQuantity() * UNIT_PRICE;
        TccTransaction transaction = new TccTransaction();
        transaction.setXid(xid);
        transaction.setStatus(TccTransactionStatus.TRYING);
        transaction.setExpireTime(LocalDateTime.now().plusMinutes(5));
        transaction.setRetryCount(0);
        tccTransactionMapper.insert(transaction);
        TccOrder order = new TccOrder();
        order.setOrderNo(orderNo);
        order.setXid(xid);
        order.setUserId(request.getUserId());
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        order.setAmount(amount);
        order.setStatus(TccOrderStatus.PENDING);
        tccParticipantMapper.insertOrder(order);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("xid", xid);
        payload.put("orderNo", orderNo);
        payload.put("userId", request.getUserId());
        payload.put("productId", request.getProductId());
        payload.put("quantity", request.getQuantity());
        payload.put("amount", amount);
        payload.put("forceFailure", request.isForceFailure());
        List<TccParticipant> tried = new java.util.ArrayList<>();
        for (TccParticipant participant : participants) {
            try {
                participant.tryPhase(xid, payload);
                recordBranch(xid, participant.branchId(), payload, TccBranchStatus.TRIED, null);
                tried.add(participant);
            } catch (Exception ex) {
                log.warn("tcc try phase failed xid={} branch={} reason={}", xid, participant.branchId(), ex.getMessage());
                cancelAll(xid, tried, payload);
                return TccResultResponse.rolledBack(xid, "try failed on " + participant.branchId() + ": " + ex.getMessage());
            }
        }

        tccTransactionMapper.updateStatus(xid, TccTransactionStatus.CONFIRMING.getCode());
        boolean confirmed = true;
        StringBuilder failure = new StringBuilder();
        for (TccParticipant participant : tried) {
            try {
                participant.confirmPhase(xid, payload);
                tccTransactionMapper.updateBranchStatus(xid, participant.branchId(),
                        TccBranchStatus.CONFIRMED.getCode(), "");
            } catch (Exception ex) {
                confirmed = false;
                failure.append(participant.branchId()).append(": ").append(ex.getMessage()).append("; ");
                log.error("tcc confirm failed xid={} branch={}", xid, participant.branchId(), ex);
            }
        }

        if (confirmed) {
            tccTransactionMapper.updateStatus(xid, TccTransactionStatus.CONFIRMED.getCode());
            tccParticipantMapper.updateOrderStatus(xid, TccOrderStatus.CONFIRMED.getCode());
            log.info("tcc transaction confirmed xid={}", xid);
            return TccResultResponse.committed(xid);
        }

        tccTransactionMapper.updateStatus(xid, TccTransactionStatus.CONFIRMING.getCode());
        return TccResultResponse.rolledBack(xid, "confirm failed, retry scheduled: " + failure);
    }

    @Override
    public Map<String, Object> status(String xid) {
        TccTransaction transaction = tccTransactionMapper.selectByXid(xid);
        if (transaction == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "tcc transaction " + xid + " not found");
        }
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("xid", transaction.getXid());
        status.put("status", transaction.getStatus() == null ? null : transaction.getStatus().name());
        status.put("retryCount", transaction.getRetryCount());
        status.put("branches", branches(xid).stream()
                .map(branch -> Map.of("branchId", branch.getBranchId(),
                        "status", branch.getStatus() == null ? null : branch.getStatus().name(),
                        "errorMessage", branch.getErrorMessage() == null ? "" : branch.getErrorMessage()))
                .toList());
        return status;
    }

    @Override
    public List<TccBranch> branches(String xid) {
        return tccTransactionMapper.selectBranches(xid);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int recoverPending() {
        List<TccTransaction> pending = tccTransactionMapper.selectByStatus(
                TccTransactionStatus.CONFIRMING.getCode(), 100);
        if (pending.isEmpty()) {
            return 0;
        }

        int recovered = 0;
        for (TccTransaction transaction : pending) {
            String xid = transaction.getXid();
            List<TccBranch> branches = tccTransactionMapper.selectBranches(xid);
            boolean allConfirmed = branches.stream()
                    .allMatch(branch -> branch.getStatus() == TccBranchStatus.CONFIRMED);
            if (allConfirmed && !branches.isEmpty()) {
                tccTransactionMapper.updateStatus(xid, TccTransactionStatus.CONFIRMED.getCode());
                tccParticipantMapper.updateOrderStatus(xid, TccOrderStatus.CONFIRMED.getCode());
                recovered++;
                log.info("tcc transaction recovered xid={}", xid);
            } else {
                tccTransactionMapper.updateStatus(xid, TccTransactionStatus.CANCELLING.getCode());
                cancelBranches(xid, branches);
                recovered++;
                log.info("tcc transaction rolled back by recovery xid={}", xid);
            }
        }
        return recovered;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void seed(Long userId, Long productId, int available, long balance) {
        TccInventory inventory = tccParticipantMapper.selectInventory(productId);
        if (inventory == null) {
            inventory = new TccInventory();
            inventory.setProductId(productId);
            inventory.setAvailable(available);
            inventory.setFrozen(0);
            tccParticipantMapper.insertInventory(inventory);
        } else {
            inventory.setAvailable(available);
            tccParticipantMapper.freezeInventory(productId, 0);
        }

        TccAccount account = tccParticipantMapper.selectAccount(userId);
        if (account == null) {
            account = new TccAccount();
            account.setUserId(userId);
            account.setBalance(balance);
            account.setFrozen(0L);
            tccParticipantMapper.insertAccount(account);
        }
        log.info("tcc participants seeded user={} balance={} product={} available={}", userId, balance, productId, available);
    }

    private void cancelAll(String xid, List<TccParticipant> tried, Map<String, Object> payload) {
        tccTransactionMapper.updateStatus(xid, TccTransactionStatus.CANCELLING.getCode());
        for (TccParticipant participant : tried) {
            try {
                participant.cancelPhase(xid, payload);
                tccTransactionMapper.updateBranchStatus(xid, participant.branchId(),
                        TccBranchStatus.CANCELLED.getCode(), "");
            } catch (Exception ex) {
                log.error("tcc cancel failed xid={} branch={}", xid, participant.branchId(), ex);
                tccTransactionMapper.updateBranchStatus(xid, participant.branchId(),
                        TccBranchStatus.TRIED.getCode(), ex.getMessage());
            }
        }
        tccTransactionMapper.updateStatus(xid, TccTransactionStatus.CANCELLED.getCode());
        tccParticipantMapper.updateOrderStatus(xid, TccOrderStatus.CANCELLED.getCode());
    }

    private void cancelBranches(String xid, List<TccBranch> branches) {
        Map<String, Object> payload = branches.isEmpty()
                ? Map.of()
                : JsonUtils.fromJson(branches.get(0).getPayload(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        for (TccBranch branch : branches) {
            if (branch.getStatus() == TccBranchStatus.CANCELLED) {
                continue;
            }
            participants.stream()
                    .filter(participant -> participant.branchId().equals(branch.getBranchId()))
                    .forEach(participant -> {
                        try {
                            participant.cancelPhase(xid, payload);
                            tccTransactionMapper.updateBranchStatus(xid, participant.branchId(),
                                    TccBranchStatus.CANCELLED.getCode(), "");
                        } catch (Exception ex) {
                            log.error("tcc recovery cancel failed xid={} branch={}", xid, participant.branchId(), ex);
                        }
                    });
        }
        tccTransactionMapper.updateStatus(xid, TccTransactionStatus.CANCELLED.getCode());
        tccParticipantMapper.updateOrderStatus(xid, TccOrderStatus.CANCELLED.getCode());
    }

    private void recordBranch(String xid, String branchId, Map<String, Object> payload,
                              TccBranchStatus status, String errorMessage) {
        TccBranch branch = new TccBranch();
        branch.setXid(xid);
        branch.setBranchId(branchId);
        branch.setStatus(status);
        branch.setPayload(JsonUtils.toJson(payload));
        branch.setErrorMessage(errorMessage == null ? "" : errorMessage);
        branch.setNextRetryTime(LocalDateTime.now());
        branch.setRetryCount(0);
        tccTransactionMapper.insertBranch(branch);
    }

    private int maxRetry() {
        return MAX_RETRY;
    }

}
