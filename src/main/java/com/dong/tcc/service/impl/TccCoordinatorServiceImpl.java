package com.dong.tcc.service.impl;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.common.util.JsonUtils;
import com.dong.common.util.Snowflake;
import com.dong.tcc.dto.TccOrderRequest;
import com.dong.tcc.dto.TccResultResponse;
import com.dong.tcc.entity.TccAccount;
import com.dong.tcc.entity.TccBranch;
import com.dong.tcc.entity.TccInventory;
import com.dong.tcc.entity.TccOrder;
import com.dong.tcc.entity.TccTransaction;
import com.dong.tcc.enums.TccBranchStatus;
import com.dong.tcc.enums.TccOrderStatus;
import com.dong.tcc.enums.TccTransactionStatus;
import com.dong.tcc.mapper.TccParticipantMapper;
import com.dong.tcc.mapper.TccTransactionMapper;
import com.dong.tcc.service.TccCoordinatorService;
import com.dong.tcc.service.TccParticipant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TCC 协调者实现。依次驱动各参与者的 Try、Confirm、Cancel。
 *
 * <p>三个必须处理的问题：
 * 幂等靠分支表的唯一索引；
 * 空回滚是 Try 未执行却收到 Cancel；
 * 悬挂是 Cancel 先到、Try 后到，需要在 Try 前检查事务状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TccCoordinatorServiceImpl implements TccCoordinatorService {

    private static final String ORDER_NO_PREFIX = "TC";

    private static final int MAX_RETRY = 5;

    private static final long UNIT_PRICE = 1000L;

    /**
     * tccTransactionMapper，MyBatis Mapper 数据访问层。
     */
    private final TccTransactionMapper tccTransactionMapper;

    /**
     * tccParticipantMapper，MyBatis Mapper 数据访问层。
     */
    private final TccParticipantMapper tccParticipantMapper;

    /**
     * TCC 参与者列表。
     */
    private final List<TccParticipant> participants;

    /**
     * 雪花 ID 生成器。
     */
    private final Snowflake snowflake;

    /**
     * 提交事务。Try 阶段只要有一个参与者失败就立即回滚已成功的分支，
     * Confirm 阶段失败则把事务留在 CONFIRMING 状态，交给恢复任务重试。
     */
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
                tccTransactionMapper.updateBranchStatus(xid, participant.branchId(), TccBranchStatus.CONFIRMED.getCode(), "");
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

    /**
     * 查询事务状态。
     */
    @Override
    public Map<String, Object> status(String xid) {
        TccTransaction transaction = tccTransactionMapper.selectByXid(xid);
        if (transaction == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "tcc transaction " + xid + " not found");
        }
        // Map.of 不允许 null value：分支或事务状态为 null（脏数据）时会直接 NPE，必须逐个 put
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("xid", transaction.getXid());
        status.put("status", transaction.getStatus() == null ? "" : transaction.getStatus().name());
        status.put("retryCount", transaction.getRetryCount());
        List<Map<String, Object>> branchList = new java.util.ArrayList<>();
        for (TccBranch branch : branches(xid)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("branchId", branch.getBranchId());
            item.put("status", branch.getStatus() == null ? "" : branch.getStatus().name());
            item.put("errorMessage", branch.getErrorMessage() == null ? "" : branch.getErrorMessage());
            branchList.add(item);
        }
        status.put("branches", branchList);
        return status;
    }

    /**
     * 查询事务的各分支记录。
     */
    @Override
    public List<TccBranch> branches(String xid) {
        return tccTransactionMapper.selectBranches(xid);
    }

    /**
     * 恢复悬挂事务。分支全部已确认则推进为已提交；有分支已确认但未全部确认时
     * 只能向前补齐 confirm（回滚已确认分支等于把钱再加回去）；完全没有确认过的
     * 事务才整单回滚。取消中的事务同样要重试，否则取消失败分支的冻结资源永久泄漏。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int recoverPending() {
        List<TccTransaction> pending = new java.util.ArrayList<>();
        pending.addAll(tccTransactionMapper.selectByStatus(TccTransactionStatus.CONFIRMING.getCode(), 100));
        pending.addAll(tccTransactionMapper.selectByStatus(TccTransactionStatus.CANCELLING.getCode(), 100));
        if (pending.isEmpty()) {
            return 0;
        }

        int recovered = 0;
        for (TccTransaction transaction : pending) {
            String xid = transaction.getXid();
            List<TccBranch> branches = tccTransactionMapper.selectBranches(xid);
            if (branches.isEmpty()) {
                continue;
            }
            boolean anyConfirmed = branches.stream().anyMatch(branch -> branch.getStatus() == TccBranchStatus.CONFIRMED);
            boolean allConfirmed = branches.stream().allMatch(branch -> branch.getStatus() == TccBranchStatus.CONFIRMED);
            if (allConfirmed) {
                tccTransactionMapper.updateStatus(xid, TccTransactionStatus.CONFIRMED.getCode());
                tccParticipantMapper.updateOrderStatus(xid, TccOrderStatus.CONFIRMED.getCode());
                recovered++;
                log.info("tcc transaction recovered xid={}", xid);
            } else if (anyConfirmed) {
                if (retryConfirm(xid, branches)) {
                    recovered++;
                    log.info("tcc transaction confirm completed by recovery xid={}", xid);
                }
            } else {
                tccTransactionMapper.updateStatus(xid, TccTransactionStatus.CANCELLING.getCode());
                if (cancelBranches(xid, branches)) {
                    recovered++;
                    log.info("tcc transaction rolled back by recovery xid={}", xid);
                }
            }
        }
        return recovered;
    }

    /**
     * 初始化演示用的库存与账户。
     */
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
            // 已有记录时必须真正落库：只改内存实体的话传入的可用量会被静默丢弃
            tccParticipantMapper.updateInventory(productId, available);
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

    /**
     * 回滚已 Try 成功的分支。回滚失败的分支会被标回 TRIED 状态，
     * 事务保持 CANCELLING 等待恢复任务再次尝试；
     * 若立即置为 CANCELLED 终态，恢复任务不再扫描，冻结资源就永久泄漏了。
     */
    private void cancelAll(String xid, List<TccParticipant> tried, Map<String, Object> payload) {
        tccTransactionMapper.updateStatus(xid, TccTransactionStatus.CANCELLING.getCode());
        boolean allCancelled = true;
        for (TccParticipant participant : tried) {
            try {
                participant.cancelPhase(xid, payload);
                tccTransactionMapper.updateBranchStatus(xid, participant.branchId(), TccBranchStatus.CANCELLED.getCode(), "");
            } catch (Exception ex) {
                log.error("tcc cancel failed xid={} branch={}", xid, participant.branchId(), ex);
                tccTransactionMapper.updateBranchStatus(xid, participant.branchId(), TccBranchStatus.TRIED.getCode(), ex.getMessage());
                allCancelled = false;
            }
        }
        if (allCancelled) {
            tccTransactionMapper.updateStatus(xid, TccTransactionStatus.CANCELLED.getCode());
            tccParticipantMapper.updateOrderStatus(xid, TccOrderStatus.CANCELLED.getCode());
        }
    }

    /**
     * 恢复路径：取消指定事务下尚未取消的分支。
     * 已 CONFIRMED 的分支必须跳过——confirm 已把冻结扣掉，cancel 再加回去就是凭空造钱。
     * 有分支取消失败时事务留在 CANCELLING，下一轮恢复任务继续重试。
     *
     * @return 是否全部分支到达取消终态
     */
    private boolean cancelBranches(String xid, List<TccBranch> branches) {
        boolean allCancelled = true;
        for (TccBranch branch : branches) {
            if (branch.getStatus() == TccBranchStatus.CANCELLED || branch.getStatus() == TccBranchStatus.CONFIRMED) {
                continue;
            }
            Map<String, Object> payload = payloadOf(branch);
            boolean matched = false;
            for (TccParticipant participant : participants) {
                if (!participant.branchId().equals(branch.getBranchId())) {
                    continue;
                }
                matched = true;
                try {
                    participant.cancelPhase(xid, payload);
                    tccTransactionMapper.updateBranchStatus(xid, participant.branchId(), TccBranchStatus.CANCELLED.getCode(), "");
                } catch (Exception ex) {
                    log.error("tcc recovery cancel failed xid={} branch={}", xid, participant.branchId(), ex);
                    allCancelled = false;
                }
            }
            if (!matched) {
                log.warn("tcc recovery cancel skipped, participant not found xid={} branch={}", xid, branch.getBranchId());
            }
        }
        if (allCancelled) {
            tccTransactionMapper.updateStatus(xid, TccTransactionStatus.CANCELLED.getCode());
            tccParticipantMapper.updateOrderStatus(xid, TccOrderStatus.CANCELLED.getCode());
        }
        return allCancelled;
    }

    /**
     * 恢复路径：向前补齐 confirm。只重试尚未确认的分支，
     * 已确认的分支再次执行 confirm 会把 frozen 重复扣减，幂等只能靠分支状态保证。
     * 补齐失败时事务留在 CONFIRMING，下一轮恢复任务继续。
     *
     * @return 是否全部分支到达确认终态
     */
    private boolean retryConfirm(String xid, List<TccBranch> branches) {
        boolean allConfirmed = true;
        for (TccBranch branch : branches) {
            if (branch.getStatus() == TccBranchStatus.CONFIRMED) {
                continue;
            }
            Map<String, Object> payload = payloadOf(branch);
            boolean matched = false;
            for (TccParticipant participant : participants) {
                if (!participant.branchId().equals(branch.getBranchId())) {
                    continue;
                }
                matched = true;
                try {
                    participant.confirmPhase(xid, payload);
                    tccTransactionMapper.updateBranchStatus(xid, participant.branchId(), TccBranchStatus.CONFIRMED.getCode(), "");
                } catch (Exception ex) {
                    log.error("tcc recovery confirm failed xid={} branch={}", xid, participant.branchId(), ex);
                    allConfirmed = false;
                }
            }
            if (!matched) {
                log.warn("tcc recovery confirm skipped, participant not found xid={} branch={}", xid, branch.getBranchId());
            }
        }
        if (allConfirmed) {
            tccTransactionMapper.updateStatus(xid, TccTransactionStatus.CONFIRMED.getCode());
            tccParticipantMapper.updateOrderStatus(xid, TccOrderStatus.CONFIRMED.getCode());
        }
        return allConfirmed;
    }

    /**
     * 反序列化分支 payload。空或非法内容时返回空 Map，
     * 恢复路径不能因为一条坏 payload 就中断整批事务的恢复。
     */
    private Map<String, Object> payloadOf(TccBranch branch) {
        if (branch.getPayload() == null || branch.getPayload().isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> payload = JsonUtils.fromJson(branch.getPayload(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });
            return payload == null ? Map.of() : payload;
        } catch (RuntimeException ex) {
            // 一条坏 payload 不能让整批悬挂事务的恢复全部回滚
            log.error("tcc branch payload is not valid json branch={}", branch.getBranchId(), ex);
            return Map.of();
        }
    }

    /**
     * 记录分支。errorMessage 必须用空字符串代替 null，
     * 因为该字段在数据库上有非空约束，写入 null 会让回滚本身失败。
     */
    private void recordBranch(String xid, String branchId, Map<String, Object> payload, TccBranchStatus status, String errorMessage) {
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

    /**
     * 获取最大重试次数。
     */
    private int maxRetry() {
        return MAX_RETRY;
    }

}
