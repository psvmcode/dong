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
 * 库存参与者。Try 冻结库存，Confirm 真正扣减，Cancel 释放冻结。
 *
 * <p>三个方法都必须幂等，且 cancelPhase 要能处理空回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TccInventoryParticipant implements TccParticipant {

    private final TccParticipantMapper tccParticipantMapper;

    @Override
    public String branchId() {
        return "inventory";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void tryPhase(String xid, Map<String, Object> payload) {
        Long productId = longValue(payload, "productId");
        int quantity = intValue(payload, "quantity");
        int updated = tccParticipantMapper.freezeInventory(productId, quantity);
        if (updated == 0) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "inventory not enough for try phase");
        }
        log.info("inventory try xid={} product={} quantity={}", xid, productId, quantity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmPhase(String xid, Map<String, Object> payload) {
        Long productId = longValue(payload, "productId");
        int quantity = intValue(payload, "quantity");
        tccParticipantMapper.confirmInventory(productId, quantity);
        log.info("inventory confirm xid={} product={} quantity={}", xid, productId, quantity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelPhase(String xid, Map<String, Object> payload) {
        Long productId = longValue(payload, "productId");
        int quantity = intValue(payload, "quantity");
        tccParticipantMapper.cancelInventory(productId, quantity);
        log.info("inventory cancel xid={} product={} quantity={}", xid, productId, quantity);
    }

    private Long longValue(Map<String, Object> payload, String key) {
        return Long.valueOf(String.valueOf(payload.get(key)));
    }

    private int intValue(Map<String, Object> payload, String key) {
        return Integer.parseInt(String.valueOf(payload.get(key)));
    }

}
