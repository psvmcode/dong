package com.dong.seckill.handler;

import com.dong.common.util.JsonUtils;
import com.dong.framework.mq.MessageHandler;
import com.dong.seckill.entity.SeckillOrder;
import com.dong.seckill.enums.SeckillOrderStatus;
import com.dong.seckill.mapper.SeckillActivityMapper;
import com.dong.seckill.mapper.SeckillOrderMapper;
import com.dong.seckill.service.SeckillStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * 秒杀订单异步建单。库存扣减已在 Redis 完成，这里只负责落库。
 *
 * <p>两道防重：先按活动与用户查一次，再由数据库唯一索引兜底。
 * 只依赖查询判断是不够的，并发下两笔请求可能同时通过检查。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillOrderCreatedHandler implements MessageHandler {

    private static final String TOPIC = "seckill-order-created";

    /**
     * seckillOrderMapper，MyBatis Mapper 数据访问层。
     */
    private final SeckillOrderMapper seckillOrderMapper;

    /**
     * seckillActivityMapper，MyBatis Mapper 数据访问层。建单后回写可售库存。
     */
    private final SeckillActivityMapper seckillActivityMapper;

    /**
     * 库存服务，查询 Redis 剩余库存用于回写活动表。
     */
    private final SeckillStockService seckillStockService;

    private final LongAdder created = new LongAdder();

    private final LongAdder duplicated = new LongAdder();

    /**
     * 畸形消息计数，便于观察消息质量。
     */
    private final LongAdder malformed = new LongAdder();

    /**
     * 返回监听的消息主题。
     */
    @Override
    public String topic() {
        return TOPIC;
    }

    /**
     * 处理秒杀订单创建消息，落库并统计重复订单。
     * 畸形消息（非法 JSON、字段缺失、类型不符）记日志后直接丢弃返回 true：
     * 让它无限重投只会把队列堵死，救不回一条本来就无效的消息。
     */
    @Override
    public boolean handle(String key, String payload) {
        Map<String, Object> message;
        try {
            message = JsonUtils.fromJson(payload, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            malformed.increment();
            log.error("malformed seckill message dropped key={} payload={}", key, payload, ex);
            return true;
        }
        if (message == null || message.get("orderNo") == null || message.get("activityId") == null || message.get("userId") == null || message.get("quantity") == null || message.get("unitPrice") == null || message.get("productId") == null) {
            malformed.increment();
            log.error("seckill message missing required fields dropped key={} payload={}", key, payload);
            return true;
        }
        String orderNo;
        Long activityId;
        Long userId;
        int quantity;
        BigDecimal unitPrice;
        Long productId;
        try {
            orderNo = String.valueOf(message.get("orderNo"));
            activityId = Long.valueOf(String.valueOf(message.get("activityId")));
            userId = Long.valueOf(String.valueOf(message.get("userId")));
            quantity = Integer.parseInt(String.valueOf(message.get("quantity")));
            unitPrice = new BigDecimal(String.valueOf(message.get("unitPrice")));
            productId = Long.valueOf(String.valueOf(message.get("productId")));
        } catch (Exception ex) {
            malformed.increment();
            log.error("seckill message fields not parseable dropped key={} payload={}", key, payload, ex);
            return true;
        }
        if (seckillOrderMapper.countByActivityAndUser(activityId, userId) > 0) {
            duplicated.increment();
            log.warn("duplicate seckill order rejected orderNo={} activity={} user={}", orderNo, activityId, userId);
            return true;
        }

        SeckillOrder order = new SeckillOrder();
        order.setOrderNo(orderNo);
        order.setActivityId(activityId);
        order.setProductId(productId);
        order.setUserId(userId);
        order.setQuantity(quantity);
        order.setAmount(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        order.setStatus(SeckillOrderStatus.PENDING_PAYMENT);
        try {
            seckillOrderMapper.insert(order);
            created.increment();
            syncAvailableStock(activityId);
            return true;
        } catch (DuplicateKeyException ex) {
            duplicated.increment();
            log.warn("unique index rejected a duplicate seckill order orderNo={}", orderNo);
            return true;
        }
    }

    /**
     * 建单后把 Redis 剩余库存回写活动表。实体注释承诺「由落库任务回写」，
     * 不回写的话列表接口的 availableStock 永远显示满库存，与实际严重不符。
     * 回写失败只记日志，不影响建单主流程。
     */
    private void syncAvailableStock(Long activityId) {
        try {
            seckillActivityMapper.updateAvailableStock(activityId, seckillStockService.available(activityId));
        } catch (Exception ex) {
            log.warn("sync available stock failed activity={}", activityId, ex);
        }
    }

    /**
     * 获取成功创建订单数。
     */
    public long createdCount() {
        return created.sum();
    }

    /**
     * 获取重复订单数。
     */
    public long duplicatedCount() {
        return duplicated.sum();
    }

}
