package com.dong.lab.seckill.service.impl;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import com.dong.lab.common.util.JsonUtils;
import com.dong.lab.common.util.Snowflake;
import com.dong.lab.framework.limiter.RateLimitAlgorithm;
import com.dong.lab.framework.limiter.RateLimited;
import com.dong.lab.framework.mq.MessageProducer;
import com.dong.lab.seckill.dto.SeckillActivityRequest;
import com.dong.lab.seckill.dto.SeckillReceiptResponse;
import com.dong.lab.seckill.entity.SeckillActivity;
import com.dong.lab.seckill.entity.SeckillOrder;
import com.dong.lab.seckill.enums.DeductStatus;
import com.dong.lab.seckill.enums.SeckillActivityStatus;
import com.dong.lab.seckill.enums.SeckillOrderStatus;
import com.dong.lab.seckill.mapper.SeckillActivityMapper;
import com.dong.lab.seckill.mapper.SeckillOrderMapper;
import com.dong.lab.seckill.service.SeckillOrderCreatedHandler;
import com.dong.lab.seckill.service.SeckillService;
import com.dong.lab.seckill.service.SeckillStockService;
import com.dong.lab.seckill.service.SoldOutFlag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private static final String ORDER_NO_PREFIX = "SK";

    private static final String TOPIC = "seckill-order-created";

    private final SeckillActivityMapper seckillActivityMapper;

    private final SeckillOrderMapper seckillOrderMapper;

    private final SeckillStockService seckillStockService;

    private final SoldOutFlag soldOutFlag;

    private final MessageProducer messageProducer;

    private final SeckillOrderCreatedHandler seckillOrderCreatedHandler;

    private final Snowflake snowflake;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createActivity(SeckillActivityRequest request) {
        SeckillActivity activity = request.toEntity();
        seckillActivityMapper.insert(activity);
        log.info("seckill activity created id={} stock={}", activity.getId(), activity.getTotalStock());
        return activity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int prepare(Long activityId) {
        SeckillActivity activity = requireActivity(activityId);
        seckillStockService.prepare(activityId, activity.getTotalStock());
        soldOutFlag.clear(activityId);

        int updated = seckillActivityMapper.updateStatus(activityId,
                SeckillActivityStatus.ONLINE.getCode(), activity.getVersion());
        if (updated == 0) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "activity was modified by someone else");
        }
        seckillActivityMapper.updateAvailableStock(activityId, activity.getTotalStock());
        log.info("seckill activity prepared id={} stock={}", activityId, activity.getTotalStock());
        return activity.getTotalStock();
    }

    @Override
    @RateLimited(key = "'seckill:activity:' + #activityId",
            limit = 200, window = 1, unit = TimeUnit.SECONDS,
            algorithm = RateLimitAlgorithm.TOKEN_BUCKET)
    public SeckillReceiptResponse seckill(Long activityId, Long userId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "quantity must be positive");
        }

        SeckillActivity activity = requireActivity(activityId);
        if (!isActive(activity)) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "activity is not open right now");
        }

        if (soldOutFlag.isSoldOut(activityId)) {
            return SeckillReceiptResponse.rejected("sold out");
        }

        int result = seckillStockService.deduct(activityId, userId, quantity);
        DeductStatus status = DeductStatus.fromResult(result);

        if (status == DeductStatus.SOLD_OUT) {
            soldOutFlag.mark(activityId);
            return SeckillReceiptResponse.rejected("sold out");
        }
        if (status == DeductStatus.DUPLICATED) {
            return SeckillReceiptResponse.rejected("one purchase per user");
        }
        if (status == DeductStatus.NOT_PREPARED) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "stock not prepared yet");
        }

        if (result == 0) {
            soldOutFlag.mark(activityId);
        }

        String orderNo = ORDER_NO_PREFIX + snowflake.nextId();
        BigDecimal amount = activity.getUnitPrice().multiply(BigDecimal.valueOf(quantity));

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("orderNo", orderNo);
        message.put("activityId", activityId);
        message.put("productId", activity.getProductId());
        message.put("userId", userId);
        message.put("quantity", quantity);
        message.put("unitPrice", activity.getUnitPrice());

        messageProducer.send(TOPIC, orderNo, JsonUtils.toJson(message));
        log.info("seckill reserved orderNo={} activity={} user={} remaining={}", orderNo, activityId, userId, result);

        return SeckillReceiptResponse.accepted(orderNo, result, amount);
    }

    @Override
    public int stockOf(Long activityId) {
        return seckillStockService.available(activityId);
    }

    @Override
    public List<SeckillActivity> activities() {
        return seckillActivityMapper.selectAll();
    }

    @Override
    public SeckillOrder order(String orderNo) {
        SeckillOrder order = seckillOrderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "order " + orderNo + " not found");
        }
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pay(String orderNo) {
        SeckillOrder order = order(orderNo);
        if (order.getStatus() != SeckillOrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "order is not payable: " + order.getStatus());
        }
        seckillOrderMapper.updateStatus(orderNo, SeckillOrderStatus.PAID.getCode());
        log.info("seckill order paid orderNo={}", orderNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(String orderNo) {
        SeckillOrder order = order(orderNo);
        if (order.getStatus() != SeckillOrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "order cannot be cancelled: " + order.getStatus());
        }
        seckillOrderMapper.updateStatus(orderNo, SeckillOrderStatus.CANCELLED.getCode());
        seckillStockService.rollback(order.getActivityId(), order.getUserId(), order.getQuantity());
        soldOutFlag.clear(order.getActivityId());
        log.info("seckill order cancelled and stock returned orderNo={}", orderNo);
    }

    @Override
    public Map<String, Object> runtime() {
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("soldOutShortCircuited", soldOutFlag.shortCircuitedCount());
        runtime.put("ordersCreated", seckillOrderCreatedHandler.createdCount());
        runtime.put("ordersDuplicated", seckillOrderCreatedHandler.duplicatedCount());
        return runtime;
    }

    private boolean isActive(SeckillActivity activity) {
        LocalDateTime now = LocalDateTime.now();
        return activity.getStatus() == SeckillActivityStatus.ONLINE
                && !now.isBefore(activity.getStartTime())
                && !now.isAfter(activity.getEndTime());
    }

    private SeckillActivity requireActivity(Long activityId) {
        SeckillActivity activity = seckillActivityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "activity " + activityId + " not found");
        }
        return activity;
    }

}
