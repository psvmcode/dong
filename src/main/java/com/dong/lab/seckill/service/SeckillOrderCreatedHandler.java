package com.dong.lab.seckill.service;

import com.dong.lab.common.util.JsonUtils;
import com.dong.lab.framework.mq.MessageHandler;
import com.dong.lab.seckill.entity.SeckillOrder;
import com.dong.lab.seckill.enums.SeckillOrderStatus;
import com.dong.lab.seckill.mapper.SeckillOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillOrderCreatedHandler implements MessageHandler {

    private static final String TOPIC = "seckill-order-created";

    private final SeckillOrderMapper seckillOrderMapper;

    private final LongAdder created = new LongAdder();

    private final LongAdder duplicated = new LongAdder();

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public boolean handle(String key, String payload) {
        Map<String, Object> message = JsonUtils.fromJson(payload,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        String orderNo = String.valueOf(message.get("orderNo"));
        Long activityId = Long.valueOf(String.valueOf(message.get("activityId")));
        Long userId = Long.valueOf(String.valueOf(message.get("userId")));
        int quantity = Integer.parseInt(String.valueOf(message.get("quantity")));
        BigDecimal unitPrice = new BigDecimal(String.valueOf(message.get("unitPrice")));
        if (seckillOrderMapper.countByActivityAndUser(activityId, userId) > 0) {
            duplicated.increment();
            log.warn("duplicate seckill order rejected orderNo={} activity={} user={}", orderNo, activityId, userId);
            return true;
        }

        SeckillOrder order = new SeckillOrder();
        order.setOrderNo(orderNo);
        order.setActivityId(activityId);
        order.setProductId(Long.valueOf(String.valueOf(message.get("productId"))));
        order.setUserId(userId);
        order.setQuantity(quantity);
        order.setAmount(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        order.setStatus(SeckillOrderStatus.PENDING_PAYMENT);
        try {
            seckillOrderMapper.insert(order);
            created.increment();
            return true;
        } catch (DuplicateKeyException ex) {
            duplicated.increment();
            log.warn("unique index rejected a duplicate seckill order orderNo={}", orderNo);
            return true;
        }
    }

    public long createdCount() {
        return created.sum();
    }

    public long duplicatedCount() {
        return duplicated.sum();
    }

}
