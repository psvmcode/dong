package com.dong.lab.mq.controller;

import com.dong.lab.common.result.Result;
import com.dong.lab.mq.entity.MqMessageLog;
import com.dong.lab.mq.handler.DemoOrderMessageHandler;
import com.dong.lab.mq.service.MqConsumeService;
import com.dong.lab.mq.service.MqProduceService;
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

@RestController
@RequestMapping("/api/mq")
@RequiredArgsConstructor
@Tag(name = "mq")
public class MqController {

    private final MqProduceService mqProduceService;

    private final MqConsumeService mqConsumeService;

    @PostMapping("/send")
    public Result<Void> send(@RequestParam(defaultValue = DemoOrderMessageHandler.TOPIC) String topic,
                             @RequestParam String key,
                             @RequestParam(defaultValue = "{}") String payload) {
        mqProduceService.send(topic, key, payload);
        return Result.success();
    }

    @PostMapping("/send-delayed")
    @Operation(summary = "delayed delivery, rocketmq uses delay levels, kafka parks the message, local bus schedules it")
    public Result<Void> sendDelayed(@RequestParam(defaultValue = DemoOrderMessageHandler.TOPIC) String topic,
                                    @RequestParam String key,
                                    @RequestParam(defaultValue = "10") long delaySeconds) {
        mqProduceService.sendDelayed(topic, key, "{\"delayed\":true}", Duration.ofSeconds(delaySeconds));
        return Result.success();
    }

    @PostMapping("/send-ordered")
    @Operation(summary = "ordered delivery, every message sharing a sharding key lands on the same channel or partition")
    public Result<Void> sendOrdered(@RequestParam(defaultValue = DemoOrderMessageHandler.TOPIC) String topic,
                                    @RequestParam String key,
                                    @RequestParam String shardingKey) {
        mqProduceService.sendOrdered(topic, key, "{\"seq\":" + System.currentTimeMillis() % 1000 + "}", shardingKey);
        return Result.success();
    }

    @PostMapping("/send-batch")
    public Result<Void> sendBatch(@RequestParam(defaultValue = DemoOrderMessageHandler.TOPIC) String topic,
                                  @RequestParam(defaultValue = "batch") String keyPrefix,
                                  @RequestParam(defaultValue = "10") int count) {
        mqProduceService.sendBatch(topic, keyPrefix, count);
        return Result.success();
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(mqProduceService.status());
    }

    @GetMapping("/logs")
    public Result<List<MqMessageLog>> logs(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(mqConsumeService.recent(limit));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(mqConsumeService.stats());
    }

}
