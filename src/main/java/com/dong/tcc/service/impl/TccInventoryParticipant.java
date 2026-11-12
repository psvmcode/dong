package com.dong.tcc.service.impl;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.tcc.mapper.TccParticipantMapper;
import com.dong.tcc.service.TccParticipant;
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

    /**
     * tccParticipantMapper，MyBatis Mapper 数据访问层。
     */
    private final TccParticipantMapper tccParticipantMapper;

    /**
     * 返回分支标识。
     */
    @Override
    public String branchId() {
        return "inventory";
    }

    /**
     * 冻结库存。
     */
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

    /**
     * 确认扣减库存。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmPhase(String xid, Map<String, Object> payload) {
        Long productId = longValue(payload, "productId");
        int quantity = intValue(payload, "quantity");
        tccParticipantMapper.confirmInventory(productId, quantity);
        log.info("inventory confirm xid={} product={} quantity={}", xid, productId, quantity);
    }

    /**
     * 释放冻结的库存。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelPhase(String xid, Map<String, Object> payload) {
        Long productId = longValue(payload, "productId");
        int quantity = intValue(payload, "quantity");
        tccParticipantMapper.cancelInventory(productId, quantity);
        log.info("inventory cancel xid={} product={} quantity={}", xid, productId, quantity);
    }

    /**
     * 从 payload 中读取长整型值。
     */
    private Long longValue(Map<String, Object> payload, String key) {
        Object value = payload == null ? null : payload.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "tcc payload missing " + key);
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "tcc payload invalid " + key);
        }
    }

    /**
     * 从 payload 中读取整型值。
     */
    private int intValue(Map<String, Object> payload, String key) {
        long value = longValue(payload, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "tcc payload out of int range " + key);
        }
        return (int) value;
    }

}
