package com.dong.seckill.controller;

import com.dong.common.result.Result;
import com.dong.framework.mq.MqFacade;
import com.dong.seckill.dto.SeckillActivityRequest;
import com.dong.seckill.dto.SeckillActivityResponse;
import com.dong.seckill.dto.SeckillOrderResponse;
import com.dong.seckill.dto.SeckillReceiptResponse;
import com.dong.seckill.entity.SeckillActivity;
import com.dong.seckill.service.SeckillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * 秒杀。核心思路是把库存决策从数据库搬到 Redis：
 * 用一条 Lua 脚本原子完成查余额、扣减、记录用户，全程无锁无事务，
 * 下单再交由消息队列异步处理。
 *
 * <p>四道防线依次是：限流令牌桶、本地售罄标记、Lua 原子扣减、
 * 数据库唯一索引兜底防重复购买。
 */
@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
@Tag(name = "秒杀")

public class SeckillController {

    /**
     * seckillService，业务服务层。
     */
    private final SeckillService seckillService;

    /**
     * 消息队列门面，用于查看消息通道状态。
     */
    private final MqFacade mqFacade;

    /**
     * 创建秒杀活动。
     */
    @PostMapping("/activities")
    @Operation(summary = "创建秒杀活动")
    public Result<Long> createActivity(@Valid @RequestBody SeckillActivityRequest request) {
        return Result.success(seckillService.createActivity(request));
    }

    /**
     * 预热库存到 Redis 并开启活动。不预热的话库存全在数据库里，
     * 高并发下会直接被打穿。
     */
    @PostMapping("/activities/{id}/prepare")
    @Operation(summary = "预热库存到 Redis 并开启活动")
    public Result<Integer> prepare(@PathVariable Long id) {
        return Result.success(seckillService.prepare(id));
    }

    /**
     * 下单。先在 Redis 扣库存，扣成功才发消息异步建单。
     * 返回的是受理凭证而不是订单，因为订单还没落库。
     */
    @PostMapping("/activities/{id}/seckill")
    @Operation(summary = "秒杀下单，先扣 Redis 库存再异步建单")
    public Result<SeckillReceiptResponse> seckill(@PathVariable Long id,
                                                  @RequestParam Long userId,
                                                  @RequestParam(defaultValue = "1") int quantity) {
        return Result.success(seckillService.seckill(id, userId, quantity));
    }

    /**
     * 查询 Redis 中的剩余库存。
     */
    @GetMapping("/activities/{id}/stock")
    @Operation(summary = "查询 Redis 中的剩余库存")
    public Result<Integer> stock(@PathVariable Long id) {
        return Result.success(seckillService.stockOf(id));
    }

    /**
     * 查询全部活动。
     */
    @GetMapping("/activities")
    @Operation(summary = "查询全部秒杀活动")
    public Result<List<SeckillActivityResponse>> activities() {
        List<SeckillActivity> activities = seckillService.activities();
        return Result.success(activities.stream().map(SeckillActivityResponse::from).toList());
    }

    /**
     * 按订单号查询订单。
     */
    @GetMapping("/orders/{orderNo}")
    @Operation(summary = "按订单号查询秒杀订单")
    public Result<SeckillOrderResponse> order(@PathVariable String orderNo) {
        return Result.success(SeckillOrderResponse.from(seckillService.order(orderNo)));
    }

    /**
     * 支付。
     */
    @PostMapping("/orders/{orderNo}/pay")
    @Operation(summary = "支付秒杀订单")
    public Result<Void> pay(@PathVariable String orderNo) {
        seckillService.pay(orderNo);
        return Result.success();
    }

    /**
     * 取消订单并回滚库存。超时未支付由定时任务自动调用，也可手工触发。
     */
    @PostMapping("/orders/{orderNo}/cancel")
    @Operation(summary = "取消订单并回滚库存")
    public Result<Void> cancel(@PathVariable String orderNo) {
        seckillService.cancel(orderNo);
        return Result.success();
    }

    /**
     * 查看运行时状态，含售罄标记与消息通道情况。
     */
    @GetMapping("/runtime")
    @Operation(summary = "查看秒杀运行时状态")
    public Result<Map<String, Object>> runtime() {
        Map<String, Object> runtime = new LinkedHashMap<>(seckillService.runtime());
        runtime.put("mq", mqFacade.status());
        return Result.success(runtime);
    }

}
