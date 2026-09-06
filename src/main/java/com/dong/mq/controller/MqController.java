package com.dong.mq.controller;

import com.dong.common.result.Result;
import com.dong.mq.entity.MqMessageLog;
import com.dong.mq.handler.DemoOrderMessageHandler;
import com.dong.mq.service.MqConsumeService;
import com.dong.mq.service.MqProduceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;
/**
 * 消息实验入口。业务代码只依赖 MessageProducer 接口，
 * 具体走本地总线、RocketMQ 还是 Kafka 由 MqFacade 按配置路由，切换不需要改代码。
 */
@RestController
@RequestMapping("/api/mq")
@RequiredArgsConstructor
@Tag(name = "消息")

public class MqController {

    /**
     * mqProduceService，业务服务层。
     */
    private final MqProduceService mqProduceService;

    /**
     * mqConsumeService，业务服务层。
     */
    private final MqConsumeService mqConsumeService;

    /**
     * 发送普通消息。
     */
    @PostMapping("/send")
    @Operation(summary = "发送普通消息")
    public Result<Void> send(@RequestParam(defaultValue = DemoOrderMessageHandler.TOPIC) String topic,
                             @RequestParam String key,
                             @RequestParam(defaultValue = "{}") String payload) {
        mqProduceService.send(topic, key, payload);
        return Result.success();
    }

    /**
     * 发送延迟消息。三种传输实现机制完全不同：
     * RocketMQ 只有十八个固定延迟等级，Kafka 靠消费端暂存，
     * 本地总线用定时调度。精度要求高时不能依赖 RocketMQ 的延迟等级。
     */
    @PostMapping("/send-delayed")
    @Operation(summary = "发送延迟消息，三种传输实现机制不同")
    public Result<Void> sendDelayed(@RequestParam(defaultValue = DemoOrderMessageHandler.TOPIC) String topic,
                                    @RequestParam String key,
                                    @RequestParam(defaultValue = "10") long delaySeconds) {
        mqProduceService.sendDelayed(topic, key, "{\"delayed\":true}", Duration.ofSeconds(delaySeconds));
        return Result.success();
    }

    /**
     * 发送顺序消息。相同 shardingKey 的消息进入同一队列或分区，
     * 从而按发送顺序被消费。
     */
    @PostMapping("/send-ordered")
    @Operation(summary = "发送顺序消息，相同分片的消息按序消费")
    public Result<Void> sendOrdered(@RequestParam(defaultValue = DemoOrderMessageHandler.TOPIC) String topic,
                                    @RequestParam String key,
                                    @RequestParam String shardingKey) {
        mqProduceService.sendOrdered(topic, key, "{\"seq\":" + System.currentTimeMillis() % 1000 + "}", shardingKey);
        return Result.success();
    }

    /**
     * 批量发送。
     */
    @PostMapping("/send-batch")
    @Operation(summary = "批量发送消息")
    public Result<Void> sendBatch(@RequestParam(defaultValue = DemoOrderMessageHandler.TOPIC) String topic,
                                  @RequestParam(defaultValue = "batch") String keyPrefix,
                                  @RequestParam(defaultValue = "10") int count) {
        mqProduceService.sendBatch(topic, keyPrefix, count);
        return Result.success();
    }

    /**
     * 查看当前生效的传输实现。
     */
    @GetMapping("/status")
    @Operation(summary = "查看当前生效的消息传输实现")
    public Result<Map<String, Object>> status() {
        return Result.success(mqProduceService.status());
    }

    /**
     * 查看投递日志。
     */
    @GetMapping("/logs")
    @Operation(summary = "查看消息投递日志")
    public Result<List<MqMessageLog>> logs(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(mqConsumeService.recent(limit));
    }

    /**
     * 查看消费统计。duplicated 计数来自消息日志的唯一索引，
     * 可以验证重复投递是否被幂等拦截。
     */
    @GetMapping("/stats")
    @Operation(summary = "查看消费统计，含重复投递计数")
    public Result<Map<String, Object>> stats() {
        return Result.success(mqConsumeService.stats());
    }

}
