package com.dong.redpacket.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import com.dong.common.result.Result;
import com.dong.redpacket.dto.GrabResultResponse;
import com.dong.redpacket.dto.RedPacketResponse;
import com.dong.redpacket.dto.RedPacketSendRequest;
import com.dong.redpacket.entity.RedPacketRecord;
import com.dong.redpacket.service.RedPacketService;
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
 * 抢红包。金额在发红包时按份算好并落库，Redis 队列只是这份数据的副本：
 * 抢的时候一次原子弹出，副本丢了能从库里原样重建，Redis 整体不可用还能降级到数据库。
 */
@RestController
@Validated
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
    public Result<GrabResultResponse> grab(@RequestParam
 @NotBlank @Size(max = 128) String packetNo, @RequestParam Long userId) {
        return Result.success(redPacketService.grab(packetNo, userId));
    }

    /**
     * 查询红包详情。
     */
    @GetMapping
    @Operation(summary = "查询红包详情")
    public Result<RedPacketResponse> detail(@RequestParam
 @NotBlank @Size(max = 128) String packetNo) {
        return Result.success(RedPacketResponse.from(redPacketService.findByPacketNo(packetNo)));
    }

    /**
     * 查询领取记录。可用它核对金额是否精确守恒。
     */
    @GetMapping("/records")
    @Operation(summary = "查询红包领取记录")
    public Result<List<RedPacketRecord>> records(@RequestParam
 @NotBlank @Size(max = 128) String packetNo) {
        return Result.success(redPacketService.records(packetNo));
    }

    /**
     * 查询剩余份数与剩余金额。
     */
    @GetMapping("/remain")
    @Operation(summary = "查询红包剩余份数与剩余金额")
    public Result<java.util.Map<String, Object>> remain(@RequestParam
 @NotBlank @Size(max = 128) String packetNo) {
        return Result.success(java.util.Map.of(
                "remainCount", redPacketService.remainCount(packetNo),
                "remainAmount", redPacketService.remainAmount(packetNo)));
    }

    /**
     * 手动从数据库重建 Redis 库存。
     */
    @PostMapping("/rebuild")
    @Operation(summary = "从数据库重建红包库存，用于模拟副本丢失后的恢复")
    public Result<Boolean> rebuild(@RequestParam
 @NotBlank @Size(max = 128) String packetNo) {
        return Result.success(redPacketService.rebuild(packetNo));
    }

    /**
     * 查看运行时状态，含限流拒绝数、降级次数与重建次数。
     */
    @GetMapping("/runtime")
    @Operation(summary = "查看抢红包运行时状态")
    public Result<java.util.Map<String, Object>> runtime() {
        return Result.success(redPacketService.runtime());
    }

}
