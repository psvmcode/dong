package com.dong.lab.order.service.impl;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import com.dong.lab.common.util.Snowflake;
import com.dong.lab.order.dto.OrderBenchmarkResponse;
import com.dong.lab.order.dto.OrderCreateRequest;
import com.dong.lab.order.dto.OrderFireRequest;
import com.dong.lab.order.dto.OrderResponse;
import com.dong.lab.order.dto.OrderTransitionLogResponse;
import com.dong.lab.order.entity.OrderTransitionLog;
import com.dong.lab.order.entity.TradeOrder;
import com.dong.lab.order.enums.OrderEvent;
import com.dong.lab.order.enums.OrderStatus;
import com.dong.lab.order.machine.OrderContext;
import com.dong.lab.order.machine.OrderStateMachine;
import com.dong.lab.order.mapper.OrderTransitionLogMapper;
import com.dong.lab.order.mapper.TradeOrderMapper;
import com.dong.lab.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * 订单履约实现。状态机只负责回答「这一步能不能走」，
 * 真正落库时还要再过一次数据库的乐观锁，两次校验缺一不可：
 * 少了状态机，非法跃迁能绕过；少了乐观锁，两个线程能同时推进同一个订单。
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class OrderServiceImpl implements OrderService {

    private static final String ORDER_NO_PREFIX = "TO";

    private static final String DEFAULT_OPERATOR = "system";

    private static final String MODE_CAS = "cas";

    private static final String MODE_NONE = "none";

    private static final int DEFAULT_RECENT_LIMIT = 20;

    private static final int MAX_BENCHMARK_THREADS = 64;

    private static final int BENCHMARK_TIMEOUT_SECONDS = 30;

    private static final long BENCHMARK_USER_ID = 0L;

    private static final BigDecimal BENCHMARK_PAY_AMOUNT = new BigDecimal("99.00");

    /**
     * orderMapper，MyBatis Mapper 数据访问层。
     */
    private final TradeOrderMapper orderMapper;

    /**
     * logMapper，MyBatis Mapper 数据访问层。
     */
    private final OrderTransitionLogMapper logMapper;

    /**
     * stateMachine，订单履约状态机。
     */
    private final OrderStateMachine stateMachine;

    /**
     * snowflake，分布式唯一 ID 生成器。
     */
    private final Snowflake snowflake;

    /**
     * 创建订单，订单号用雪花算法生成，初始状态固定为待支付。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(OrderCreateRequest request) {
        String orderNo = ORDER_NO_PREFIX + snowflake.nextId();
        TradeOrder order = request.toEntity();
        order.setOrderNo(orderNo);
        orderMapper.insert(order);
        log.info("order created orderNo={} payAmount={}", orderNo, order.getPayAmount());
        return orderNo;
    }

    /**
     * 触发事件推进订单状态，对外只暴露带乐观锁的版本。
     */
    @Override
    public OrderResponse fire(String orderNo, OrderFireRequest request) {
        return fireInternal(orderNo, request, true);
    }

    /**
     * 查询订单详情。
     */
    @Override
    public OrderResponse detail(String orderNo) {
        return OrderResponse.from(requireOrder(orderNo));
    }

    /**
     * 查询订单的流转日志。
     */
    @Override
    public List<OrderTransitionLogResponse> logs(String orderNo) {
        List<OrderTransitionLogResponse> responses = new ArrayList<>();
        for (OrderTransitionLog item : logMapper.selectByOrderNo(orderNo)) {
            responses.add(OrderTransitionLogResponse.from(item));
        }
        return responses;
    }

    /**
     * 查询最近创建的订单。
     */
    @Override
    public List<OrderResponse> recent(int limit) {
        List<OrderResponse> responses = new ArrayList<>();
        for (TradeOrder order : orderMapper.selectRecent(limit <= 0 ? DEFAULT_RECENT_LIMIT : limit)) {
            responses.add(OrderResponse.from(order));
        }
        return responses;
    }

    /**
     * 查询当前状态可触发的事件，拿全部事件挨个问状态机这条路能不能走。
     */
    @Override
    public List<String> availableEvents(String orderNo) {
        OrderStatus status = requireOrder(orderNo).getStatus();
        List<String> events = new ArrayList<>();
        for (OrderEvent event : OrderEvent.values()) {
            if (stateMachine.supports(status, event)) {
                events.add(event.name());
            }
        }
        return events;
    }

    /**
     * 并发对比实验。先造一张已支付的订单，再让多个线程同时发货。
     * 带乐观锁时只有一次能成功，不带时每一次都会覆盖前一次，
     * 差别体现在 successCount 与 version 增量上。
     */
    @Override
    public OrderBenchmarkResponse benchmark(int threads, String mode) {
        int size = Math.max(2, Math.min(threads, MAX_BENCHMARK_THREADS));
        boolean guarded = !MODE_NONE.equalsIgnoreCase(mode);
        String orderNo = createBenchmarkOrder();
        payForBenchmark(orderNo);
        int baseline = logMapper.countByOrderNo(orderNo);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger blocked = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(size);
        ExecutorService pool = Executors.newFixedThreadPool(size);
        long startAt = System.currentTimeMillis();
        for (int i = 0; i < size; i++) {
            pool.execute(() -> {
                try {
                    ready.await();
                    shipForBenchmark(orderNo, guarded);
                    success.incrementAndGet();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (Exception ex) {
                    blocked.incrementAndGet();
                } finally {
                    finished.countDown();
                }
            });
        }
        ready.countDown();
        awaitFinish(finished);
        pool.shutdownNow();
        TradeOrder order = requireOrder(orderNo);
        OrderBenchmarkResponse response = new OrderBenchmarkResponse();
        response.setOrderNo(orderNo);
        response.setThreads(size);
        response.setMode(guarded ? MODE_CAS : MODE_NONE);
        response.setSuccessCount(success.get());
        response.setBlockedCount(blocked.get());
        response.setFinalStatus(order.getStatus() == null ? null : order.getStatus().name());
        response.setFinalVersion(order.getVersion());
        response.setAttemptLogCount(logMapper.countByOrderNo(orderNo) - baseline);
        response.setElapsedMs(System.currentTimeMillis() - startAt);
        log.info("order benchmark finished orderNo={} mode={} threads={} success={} blocked={}",
                orderNo, response.getMode(), size, success.get(), blocked.get());
        return response;
    }

    /**
     * 导出状态机图。
     */
    @Override
    public String plantUml() {
        return stateMachine.plantUml();
    }

    /**
     * 删除订单及其流转日志。
     */
    @Override
    public void remove(String orderNo) {
        logMapper.deleteByOrderNo(orderNo);
        orderMapper.deleteByOrderNo(orderNo);
    }

    /**
     * 推进状态的主流程。三道关卡依次是：迁移有没有定义、守卫过不过、数据库有没有被抢先。
     *
     * @param guarded false 时跳过乐观锁，仅供并发对比实验使用
     */
    private OrderResponse fireInternal(String orderNo, OrderFireRequest request, boolean guarded) {
        OrderEvent event = OrderEvent.ofName(request.getEvent());
        TradeOrder order = requireOrder(orderNo);
        OrderStatus from = order.getStatus();
        OrderContext context = buildContext(order, request);
        if (!stateMachine.supports(from, event)) {
            reject(order, event, from, "当前状态不支持该事件", context.getOperator());
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "order " + orderNo + " cannot handle " + event);
        }
        stateMachine.fire(from, event, context);
        if (!context.isAccepted()) {
            reject(order, event, from, "守卫条件不满足", context.getOperator());
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "guard rejected event " + event + " from " + from);
        }
        if (context.isUrge()) {
            orderMapper.increaseUrgeCount(orderNo);
            writeLog(orderNo, from, from, event, true, "", context.getOperator());
            return OrderResponse.from(requireOrder(orderNo));
        }
        return advance(order, event, context, guarded);
    }

    /**
     * 落库推进状态。乐观锁是最后一道防线，返回 0 说明状态或版本已经变了。
     */
    private OrderResponse advance(TradeOrder order, OrderEvent event, OrderContext context, boolean guarded) {
        OrderStatus from = order.getStatus();
        OrderStatus target = context.getTarget();
        TradeOrder patch = buildPatch(order, event, context, from);
        int updated = guarded
                ? orderMapper.updateStatus(patch, from)
                : orderMapper.updateStatusUnlocked(order.getOrderNo(), target);
        if (updated <= 0) {
            reject(order, event, from, "并发冲突，已有其他操作抢先推进", context.getOperator());
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, Constants.MESSAGE_OPERATION_CONFLICT);
        }
        writeLog(order.getOrderNo(), from, target, event, true, "", context.getOperator());
        return OrderResponse.from(requireOrder(order.getOrderNo()));
    }

    /**
     * 组装待写入的字段。只填本次事件相关的字段，其余保持不动，
     * 这样即使两个事件前后脚到达也不会互相覆盖无关字段。
     */
    private TradeOrder buildPatch(TradeOrder order, OrderEvent event, OrderContext context, OrderStatus from) {
        TradeOrder patch = new TradeOrder();
        patch.setOrderNo(order.getOrderNo());
        patch.setStatus(context.getTarget());
        patch.setVersion(order.getVersion());
        switch (event) {
            case PAY -> patch.setPayNo(context.getPayNo());
            case SHIP -> patch.setTrackingNo(context.getTrackingNo());
            case APPLY_REFUND -> patch.setRefundFrom(from.getCode());
            case REFUND_SUCCESS -> patch.setRefundAmount(order.getRefundAmount().add(context.getRefundAmount()));
            case REFUND_FAIL -> patch.setRefundFrom(0);
            default -> {
            }
        }
        return patch;
    }

    /**
     * 组装状态机上下文，把订单当前的状态与金额一并带进去供守卫判断。
     */
    private OrderContext buildContext(TradeOrder order, OrderFireRequest request) {
        OrderContext context = new OrderContext();
        context.setOrderNo(order.getOrderNo());
        context.setOperator(operatorOf(request));
        context.setPayAmount(order.getPayAmount());
        context.setPayNo(request.getPayNo());
        context.setTrackingNo(request.getTrackingNo());
        context.setRefundAmount(refundAmountOf(order, request));
        context.setRefundFrom(refundFromOf(order));
        return context;
    }

    /**
     * 取操作人，没传就记为系统。
     */
    private String operatorOf(OrderFireRequest request) {
        String operator = request.getOperator();
        return operator == null || operator.isBlank() ? DEFAULT_OPERATOR : operator;
    }

    /**
     * 取退款金额，没传就按剩余可退金额算，即应付减去已退。
     */
    private BigDecimal refundAmountOf(TradeOrder order, OrderFireRequest request) {
        if (request.getRefundAmount() != null) {
            return request.getRefundAmount();
        }
        return order.getPayAmount().subtract(order.getRefundAmount());
    }

    /**
     * 取发起退款前的状态，未发起过退款时为空。
     */
    private OrderStatus refundFromOf(TradeOrder order) {
        Integer refundFrom = order.getRefundFrom();
        if (refundFrom == null || refundFrom == 0) {
            return null;
        }
        return OrderStatus.of(refundFrom);
    }

    /**
     * 按订单号查订单，查不到直接抛业务异常。
     */
    private TradeOrder requireOrder(String orderNo) {
        TradeOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "order not found " + orderNo);
        }
        return order;
    }

    /**
     * 写一条流转日志，成功与失败都记。
     */
    private void writeLog(String orderNo, OrderStatus from, OrderStatus to, OrderEvent event, boolean accepted,
                          String reason, String operator) {
        OrderTransitionLog item = new OrderTransitionLog();
        item.setOrderNo(orderNo);
        item.setFromStatus(from == null ? 0 : from.getCode());
        item.setToStatus(to == null ? 0 : to.getCode());
        item.setEvent(event.name());
        item.setResult(accepted ? 1 : 0);
        item.setReason(reason == null ? "" : reason);
        item.setOperator(operator == null ? DEFAULT_OPERATOR : operator);
        logMapper.insert(item);
    }

    /**
     * 记录一次被拒绝的推进。日志照写，否则事后无从核对被拦了多少次。
     */
    private void reject(TradeOrder order, OrderEvent event, OrderStatus from, String reason, String operator) {
        log.info("order transition rejected orderNo={} event={} from={} reason={}",
                order.getOrderNo(), event, from, reason);
        writeLog(order.getOrderNo(), from, from, event, false, reason, operator);
    }

    /**
     * 造一张供实验使用的订单。
     */
    private String createBenchmarkOrder() {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setUserId(BENCHMARK_USER_ID);
        request.setProductName("benchmark-order");
        request.setQuantity(1);
        request.setPayAmount(BENCHMARK_PAY_AMOUNT);
        return create(request);
    }

    /**
     * 实验前先把订单推进到待发货。
     */
    private void payForBenchmark(String orderNo) {
        OrderFireRequest request = new OrderFireRequest();
        request.setEvent(OrderEvent.PAY.name());
        request.setOperator(DEFAULT_OPERATOR);
        request.setPayNo("PAY" + snowflake.nextId());
        fireInternal(orderNo, request, true);
    }

    /**
     * 实验动作：并发发货。
     */
    private void shipForBenchmark(String orderNo, boolean guarded) {
        OrderFireRequest request = new OrderFireRequest();
        request.setEvent(OrderEvent.SHIP.name());
        request.setOperator(DEFAULT_OPERATOR);
        request.setTrackingNo("SF" + snowflake.nextId());
        fireInternal(orderNo, request, guarded);
    }

    /**
     * 等待实验线程跑完，最多等固定时长，避免线程池异常时把接口挂死。
     */
    private void awaitFinish(CountDownLatch finished) {
        try {
            finished.await(BENCHMARK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

}
