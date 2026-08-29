package com.dong.lab.seckill.controller;

import com.dong.lab.common.result.Result;
import com.dong.lab.framework.mq.MqFacade;
import com.dong.lab.seckill.dto.SeckillActivityRequest;
import com.dong.lab.seckill.dto.SeckillActivityResponse;
import com.dong.lab.seckill.dto.SeckillOrderResponse;
import com.dong.lab.seckill.dto.SeckillReceiptResponse;
import com.dong.lab.seckill.entity.SeckillActivity;
import com.dong.lab.seckill.service.SeckillService;
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

@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
@Tag(name = "seckill")
public class SeckillController {

    private final SeckillService seckillService;

    private final MqFacade mqFacade;

    @PostMapping("/activities")
    public Result<Long> createActivity(@Valid @RequestBody SeckillActivityRequest request) {
        return Result.success(seckillService.createActivity(request));
    }

    @PostMapping("/activities/{id}/prepare")
    @Operation(summary = "push the stock into redis and open the activity")
    public Result<Integer> prepare(@PathVariable Long id) {
        return Result.success(seckillService.prepare(id));
    }

    @PostMapping("/activities/{id}/seckill")
    @Operation(summary = "buy, the stock is taken in redis and the order is created asynchronously")
    public Result<SeckillReceiptResponse> seckill(@PathVariable Long id,
                                                  @RequestParam Long userId,
                                                  @RequestParam(defaultValue = "1") int quantity) {
        return Result.success(seckillService.seckill(id, userId, quantity));
    }

    @GetMapping("/activities/{id}/stock")
    public Result<Integer> stock(@PathVariable Long id) {
        return Result.success(seckillService.stockOf(id));
    }

    @GetMapping("/activities")
    public Result<List<SeckillActivityResponse>> activities() {
        List<SeckillActivity> activities = seckillService.activities();
        return Result.success(activities.stream().map(SeckillActivityResponse::from).toList());
    }

    @GetMapping("/orders/{orderNo}")
    public Result<SeckillOrderResponse> order(@PathVariable String orderNo) {
        return Result.success(SeckillOrderResponse.from(seckillService.order(orderNo)));
    }

    @PostMapping("/orders/{orderNo}/pay")
    public Result<Void> pay(@PathVariable String orderNo) {
        seckillService.pay(orderNo);
        return Result.success();
    }

    @PostMapping("/orders/{orderNo}/cancel")
    public Result<Void> cancel(@PathVariable String orderNo) {
        seckillService.cancel(orderNo);
        return Result.success();
    }

    @GetMapping("/runtime")
    public Result<Map<String, Object>> runtime() {
        Map<String, Object> runtime = new LinkedHashMap<>(seckillService.runtime());
        runtime.put("mq", mqFacade.status());
        return Result.success(runtime);
    }

}
