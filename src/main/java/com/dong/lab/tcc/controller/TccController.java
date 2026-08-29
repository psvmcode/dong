package com.dong.lab.tcc.controller;

import com.dong.lab.common.result.Result;
import com.dong.lab.tcc.dto.TccOrderRequest;
import com.dong.lab.tcc.dto.TccResultResponse;
import com.dong.lab.tcc.entity.TccBranch;
import com.dong.lab.tcc.service.TccCoordinatorService;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tcc")
@RequiredArgsConstructor
@Tag(name = "tcc")
public class TccController {

    private final TccCoordinatorService tccCoordinatorService;

    @PostMapping("/seed")
    @Operation(summary = "create the inventory and account rows used by the demo")
    public Result<Void> seed(@RequestParam Long userId,
                             @RequestParam Long productId,
                             @RequestParam(defaultValue = "100") int available,
                             @RequestParam(defaultValue = "100000") long balance) {
        tccCoordinatorService.seed(userId, productId, available, balance);
        return Result.success();
    }

    @PostMapping("/order")
    @Operation(summary = "submit a distributed order, try then confirm, or cancel every branch")
    public Result<TccResultResponse> submit(@Valid @RequestBody TccOrderRequest request) {
        return Result.success(tccCoordinatorService.submit(request));
    }

    @GetMapping("/{xid}")
    public Result<Map<String, Object>> status(@PathVariable String xid) {
        return Result.success(tccCoordinatorService.status(xid));
    }

    @GetMapping("/{xid}/branches")
    public Result<List<TccBranch>> branches(@PathVariable String xid) {
        return Result.success(tccCoordinatorService.branches(xid));
    }

    @PostMapping("/recover")
    @Operation(summary = "settle transactions that were left in the confirming state")
    public Result<Integer> recover() {
        return Result.success(tccCoordinatorService.recoverPending());
    }

}
