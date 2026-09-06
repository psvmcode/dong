package com.dong.order.controller;

import com.dong.common.result.Result;
import com.dong.order.dto.OrderBenchmarkResponse;
import com.dong.order.dto.OrderCreateRequest;
import com.dong.order.dto.OrderFireRequest;
import com.dong.order.dto.OrderResponse;
import com.dong.order.dto.OrderTransitionLogResponse;
import com.dong.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
/**
 * 订单履约状态机。状态只能由事件推进，接口层提供不了直接改状态的入口，
 * 这是刻意的设计：绕过状态机的口子一旦开了一个，规则迟早会被绕过。
 *
 * <p>recommended 调用顺序：创建订单 → 支付 → 发货 → 确认收货。
 * 想看并发下会发生什么，直接压 benchmark 接口对比 cas 与 none 两种模式。
 */
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
@Tag(name = "订单履约状态机")

public class OrderController {

    /**
     * orderService，业务服务层。
     */
    private final OrderService orderService;

    /**
     * 创建订单，初始停在待支付。
     */
    @PostMapping
    @Operation(summary = "创建订单，初始状态为待支付")
    public Result<String> create(@Valid @RequestBody OrderCreateRequest request) {
        return Result.success(orderService.create(request));
    }

    /**
     * 触发事件推进订单状态。事件名取 OrderEvent 的枚举字面量。
     */
    @PostMapping("/{orderNo}/events")
    @Operation(summary = "触发订单事件推进状态")
    public Result<OrderResponse> fire(@PathVariable String orderNo, @Valid @RequestBody OrderFireRequest request) {
        return Result.success(orderService.fire(orderNo, request));
    }

    /**
     * 查询订单详情。
     */
    @GetMapping("/{orderNo}")
    @Operation(summary = "查询订单详情")
    public Result<OrderResponse> detail(@PathVariable String orderNo) {
        return Result.success(orderService.detail(orderNo));
    }

    /**
     * 查询当前状态下可以触发哪些事件，前端可以据此决定显示哪些按钮。
     */
    @GetMapping("/{orderNo}/available-events")
    @Operation(summary = "查询当前状态可触发的事件")
    public Result<List<String>> availableEvents(@PathVariable String orderNo) {
        return Result.success(orderService.availableEvents(orderNo));
    }

    /**
     * 查询状态流转日志，被拒绝的记录也在里面。
     */
    @GetMapping("/{orderNo}/logs")
    @Operation(summary = "查询订单状态流转日志")
    public Result<List<OrderTransitionLogResponse>> logs(@PathVariable String orderNo) {
        return Result.success(orderService.logs(orderNo));
    }

    /**
     * 查询最近的订单。
     */
    @GetMapping
    @Operation(summary = "查询最近创建的订单")
    public Result<List<OrderResponse>> recent(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(orderService.recent(limit));
    }

    /**
     * 并发对比实验。多个线程同时发货，cas 模式只有一次成功，none 模式每次都会覆盖。
     */
    @PostMapping("/benchmark")
    @Operation(summary = "并发推进对比实验，cas 带乐观锁，none 不带")
    public Result<OrderBenchmarkResponse> benchmark(@RequestParam(defaultValue = "16") int threads,
                                                    @RequestParam(defaultValue = "cas") String mode) {
        return Result.success(orderService.benchmark(threads, mode));
    }

    /**
     * 导出状态机图，PlantUML 语法，贴到支持 PlantUML 的编辑器里即可渲染。
     */
    @GetMapping("/state-machine/plantuml")
    @Operation(summary = "导出状态机图，PlantUML 语法")
    public Result<String> plantUml() {
        return Result.success(orderService.plantUml());
    }

    /**
     * 删除订单及其流转日志，实验环境清理用。
     */
    @DeleteMapping("/{orderNo}")
    @Operation(summary = "删除订单及其流转日志")
    public Result<Void> remove(@PathVariable String orderNo) {
        orderService.remove(orderNo);
        return Result.success();
    }

}
