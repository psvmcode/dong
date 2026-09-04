package com.dong.lab.tcc.service.impl;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import com.dong.lab.tcc.mapper.TccParticipantMapper;
import com.dong.lab.tcc.service.TccParticipant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
/**
 * 账户参与者。Try 冻结余额，Confirm 真正扣减，Cancel 释放冻结。
 *
 * <p>三个方法都必须幂等，且 cancelPhase 要能处理空回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class TccAccountParticipant implements TccParticipant {

    /**
     * tccParticipantMapper，MyBatis Mapper 数据访问层。
     */
    private final TccParticipantMapper tccParticipantMapper;

    /**
     * 返回分支标识。
     */
    @Override
    public String branchId() {
        return "account";
    }

    /**
     * 冻结账户余额。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void tryPhase(String xid, Map<String, Object> payload) {
        Long userId = longValue(payload, "userId");
        long amount = longValue(payload, "amount");
        if (Boolean.TRUE.equals(payload.get("forceFailure"))) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "account try phase failed on purpose");
        }

        int updated = tccParticipantMapper.freezeAccount(userId, amount);
        if (updated == 0) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "balance not enough for try phase");
        }
        log.info("account try xid={} user={} amount={}", xid, userId, amount);
    }

    /**
     * 确认扣减账户余额。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmPhase(String xid, Map<String, Object> payload) {
        Long userId = longValue(payload, "userId");
        long amount = longValue(payload, "amount");
        tccParticipantMapper.confirmAccount(userId, amount);
        log.info("account confirm xid={} user={} amount={}", xid, userId, amount);
    }

    /**
     * 释放冻结的账户余额。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelPhase(String xid, Map<String, Object> payload) {
        Long userId = longValue(payload, "userId");
        long amount = longValue(payload, "amount");
        tccParticipantMapper.cancelAccount(userId, amount);
        log.info("account cancel xid={} user={} amount={}", xid, userId, amount);
    }

    /**
     * 从 payload 中读取长整型值。
     */
    private long longValue(Map<String, Object> payload, String key) {
        return Long.parseLong(String.valueOf(payload.get(key)));
    }

}
