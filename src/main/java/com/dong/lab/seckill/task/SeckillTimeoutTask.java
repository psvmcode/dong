package com.dong.lab.seckill.task;

import com.dong.lab.seckill.entity.SeckillOrder;
import com.dong.lab.seckill.enums.SeckillOrderStatus;
import com.dong.lab.seckill.mapper.SeckillOrderMapper;
import com.dong.lab.seckill.service.SeckillStockService;
import com.dong.lab.seckill.support.SoldOutFlag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
/**
 * 超时未支付订单回收。库存扣减是即时生效的，
 * 若不回收，用户拍下不付款就会永久占用库存，这是秒杀场景必须处理的漏洞。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "lab.seckill", name = "timeout-task-enabled", havingValue = "true", matchIfMissing = true)

public class SeckillTimeoutTask {

    /**
     * seckillOrderMapper，MyBatis Mapper 数据访问层。
     */
    private final SeckillOrderMapper seckillOrderMapper;

    /**
     * seckillStockService，业务服务层。
     */
    private final SeckillStockService seckillStockService;

    /**
     * 本地售罄标记，回滚库存后需清除。
     */
    private final SoldOutFlag soldOutFlag;

    /**
     * 支付超时时间，单位分钟。
     */
    @Value("${lab.seckill.payment-timeout-minutes:15}")
    private int paymentTimeoutMinutes;

    /**
     * 回收超时订单。回滚库存后必须清掉售罄标记，
     * 否则库存虽然回来了，后续请求仍会被本地标记直接拒绝。
     */
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

    /**
     * 释放单个超时订单，回滚库存并清除售罄标记。
     */
    @Transactional(rollbackFor = Exception.class)
    public void release(SeckillOrder order) {
        seckillOrderMapper.updateStatus(order.getOrderNo(), SeckillOrderStatus.CANCELLED.getCode());
        seckillStockService.rollback(order.getActivityId(), order.getUserId(), order.getQuantity());
        soldOutFlag.clear(order.getActivityId());
    }

}
