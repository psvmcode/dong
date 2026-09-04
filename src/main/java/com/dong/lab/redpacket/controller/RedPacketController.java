package com.dong.lab.redpacket.controller;

import com.dong.lab.common.result.Result;
import com.dong.lab.redpacket.dto.GrabResultResponse;
import com.dong.lab.redpacket.dto.RedPacketResponse;
import com.dong.lab.redpacket.dto.RedPacketSendRequest;
import com.dong.lab.redpacket.entity.RedPacketRecord;
import com.dong.lab.redpacket.service.RedPacketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
/**
 * 抢红包。核心设计是发红包时就把金额算好并放进 Redis List，
 * 抢的时候只是一次原子弹出，全程没有锁也没有事务，再多人同时点也不会竞争。
 */
@RestController
@RequestMapping("/api/red-packet")
@RequiredArgsConstructor
@Tag(name = "抢红包")

public class RedPacketController {

    /**
     * redPacketService，业务服务层。
     */
    private final RedPacketService redPacketService;

    /**
     * 发红包。金额按二倍均值法预先分配好，
     * 每人期望相等又有随机性，同时为剩余人数预留最低金额，避免最后一人拿到零。
     */
    @PostMapping("/send")
    @Operation(summary = "发红包，金额预先分配并写入 Redis")
    public Result<String> send(@Valid @RequestBody RedPacketSendRequest request) {
        return Result.success(redPacketService.send(request));
    }

    /**
     * 抢红包。一次原子弹出即完成，多人并发不会重复抢到同一份。
     */
    @PostMapping("/grab")
    @Operation(summary = "抢红包，从预分配列表中原子弹出一份")
    public Result<GrabResultResponse> grab(@RequestParam String packetNo, @RequestParam Long userId) {
        return Result.success(redPacketService.grab(packetNo, userId));
    }

    /**
     * 查询红包详情。
     */
    @GetMapping
    @Operation(summary = "查询红包详情")
    public Result<RedPacketResponse> detail(@RequestParam String packetNo) {
        return Result.success(RedPacketResponse.from(redPacketService.findByPacketNo(packetNo)));
    }

    /**
     * 查询领取记录。可用它核对金额是否精确守恒。
     */
    @GetMapping("/records")
    @Operation(summary = "查询红包领取记录")
    public Result<List<RedPacketRecord>> records(@RequestParam String packetNo) {
        return Result.success(redPacketService.records(packetNo));
    }

    /**
     * 查询剩余份数与剩余金额。
     */
    @GetMapping("/remain")
    @Operation(summary = "查询红包剩余份数与剩余金额")
    public Result<java.util.Map<String, Object>> remain(@RequestParam String packetNo) {
        return Result.success(java.util.Map.of(
                "remainCount", redPacketService.remainCount(packetNo),
                "remainAmount", redPacketService.remainAmount(packetNo)));
    }

}
