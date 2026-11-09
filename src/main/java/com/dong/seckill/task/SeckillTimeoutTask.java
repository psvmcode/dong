package com.dong.seckill.task;

import com.dong.seckill.entity.SeckillOrder;
import com.dong.seckill.enums.SeckillOrderStatus;
import com.dong.seckill.mapper.SeckillOrderMapper;
import com.dong.seckill.service.SeckillStockService;
import com.dong.seckill.support.SoldOutFlag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 超时未支付订单回收。库存扣减是即时生效的，
 * 若不回收，用户拍下不付款就会永久占用库存，这是秒杀场景必须处理的漏洞。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dong.seckill", name = "timeout-task-enabled", havingValue = "true", matchIfMissing = true)
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
    @Value("${dong.seckill.payment-timeout-minutes:15}")
    private int paymentTimeoutMinutes;

    /**
     * 回收超时订单。回滚库存后必须清掉售罄标记，
     * 否则库存虽然回来了，后续请求仍会被本地标记直接拒绝。
     * 逐条 try/catch：一条订单处理失败不能中断整批，否则后面的订单本轮全部不处理。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void releaseUnpaidOrders() {
        List<SeckillOrder> candidates = seckillOrderMapper.selectTimeoutCandidates(SeckillOrderStatus.PENDING_PAYMENT.getCode(), paymentTimeoutMinutes);
        if (candidates.isEmpty()) {
            return;
        }
        int released = 0;
        for (SeckillOrder order : candidates) {
            try {
                if (release(order)) {
                    released++;
                }
            } catch (Exception ex) {
                log.error("release unpaid seckill order failed orderNo={}", order.getOrderNo(), ex);
            }
        }
        log.info("released {} unpaid seckill orders", released);
    }

    /**
     * 释放单个超时订单。条件更新以影响行数判定：订单在扫描到更新之间可能已被
     * 并发支付或取消，此时不得回滚库存，否则已支付订单的钱货两空。
     * 回滚库存失败时把订单放回待支付状态，等待下一轮回收重试，避免库存蒸发。
     *
     * @return 是否真正释放
     */
    private boolean release(SeckillOrder order) {
        int updated = seckillOrderMapper.updateStatus(order.getOrderNo(), SeckillOrderStatus.CANCELLED.getCode(), SeckillOrderStatus.PENDING_PAYMENT.getCode());
        if (updated <= 0) {
            return false;
        }
        try {
            seckillStockService.rollback(order.getActivityId(), order.getUserId(), order.getQuantity());
        } catch (Exception ex) {
            seckillOrderMapper.updateStatus(order.getOrderNo(), SeckillOrderStatus.PENDING_PAYMENT.getCode(), SeckillOrderStatus.CANCELLED.getCode());
            throw ex;
        }
        soldOutFlag.clear(order.getActivityId());
        return true;
    }

}
