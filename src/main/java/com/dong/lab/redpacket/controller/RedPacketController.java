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

@RestController
@RequestMapping("/api/red-packet")
@RequiredArgsConstructor
@Tag(name = "red-packet")
public class RedPacketController {

    private final RedPacketService redPacketService;

    @PostMapping("/send")
    @Operation(summary = "send a red packet, amounts are pre allocated and pushed into redis")
    public Result<String> send(@Valid @RequestBody RedPacketSendRequest request) {
        return Result.success(redPacketService.send(request));
    }

    @PostMapping("/grab")
    @Operation(summary = "grab one amount, one atomic pop from the pre allocated list")
    public Result<GrabResultResponse> grab(@RequestParam String packetNo, @RequestParam Long userId) {
        return Result.success(redPacketService.grab(packetNo, userId));
    }

    @GetMapping
    public Result<RedPacketResponse> detail(@RequestParam String packetNo) {
        return Result.success(RedPacketResponse.from(redPacketService.findByPacketNo(packetNo)));
    }

    @GetMapping("/records")
    public Result<List<RedPacketRecord>> records(@RequestParam String packetNo) {
        return Result.success(redPacketService.records(packetNo));
    }

    @GetMapping("/remain")
    public Result<java.util.Map<String, Object>> remain(@RequestParam String packetNo) {
        return Result.success(java.util.Map.of(
                "remainCount", redPacketService.remainCount(packetNo),
                "remainAmount", redPacketService.remainAmount(packetNo)));
    }

}
