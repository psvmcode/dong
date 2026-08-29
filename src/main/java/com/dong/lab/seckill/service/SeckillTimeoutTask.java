package com.dong.lab.seckill.service;

import com.dong.lab.seckill.entity.SeckillOrder;
import com.dong.lab.seckill.enums.SeckillOrderStatus;
import com.dong.lab.seckill.mapper.SeckillOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lab.seckill", name = "timeout-task-enabled", havingValue = "true", matchIfMissing = true)
public class SeckillTimeoutTask {

    private final SeckillOrderMapper seckillOrderMapper;

    private final SeckillStockService seckillStockService;

    private final SoldOutFlag soldOutFlag;

    @Value("${lab.seckill.payment-timeout-minutes:15}")
    private int paymentTimeoutMinutes;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void releaseUnpaidOrders() {
        List<SeckillOrder> candidates = seckillOrderMapper.selectTimeoutCandidates(
                SeckillOrderStatus.PENDING_PAYMENT.getCode(), paymentTimeoutMinutes);
        if (candidates.isEmpty()) {
            return;
        }
        candidates.forEach(this::release);
        log.info("released {} unpaid seckill orders", candidates.size());
    }

    @Transactional(rollbackFor = Exception.class)
    public void release(SeckillOrder order) {
        seckillOrderMapper.updateStatus(order.getOrderNo(), SeckillOrderStatus.CANCELLED.getCode());
        seckillStockService.rollback(order.getActivityId(), order.getUserId(), order.getQuantity());
        soldOutFlag.clear(order.getActivityId());
    }

}
