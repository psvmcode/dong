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

/**
 * 分布式事务 TCC。三阶段依次是 Try 冻结资源、Confirm 确认扣减、Cancel 释放冻结。
 *
 * <p>实现上必须处理三个问题，否则异常分支下数据会错：
 * 幂等靠分支表唯一索引防重复提交；
 * 空回滚是 Try 未执行却收到 Cancel，此时直接返回成功；
 * 悬挂是 Cancel 先到、Try 后到，需要在 Try 前检查事务状态。
 */
@RestController
@RequestMapping("/api/tcc")
@RequiredArgsConstructor
@Tag(name = "分布式事务-TCC")
public class TccController {

    private final TccCoordinatorService tccCoordinatorService;

    /**
     * 初始化演示数据。
     */
    @PostMapping("/seed")
    @Operation(summary = "初始化库存与账户，作为演示数据")
    public Result<Void> seed(@RequestParam Long userId,
                             @RequestParam Long productId,
                             @RequestParam(defaultValue = "100") int available,
                             @RequestParam(defaultValue = "100000") long balance) {
        tccCoordinatorService.seed(userId, productId, available, balance);
        return Result.success();
    }

    /**
     * 提交分布式订单。forceFailure 为 true 时强制走 Cancel，用于验证回滚。
     */
    @PostMapping("/order")
    @Operation(summary = "提交分布式订单，可选强制失败以验证回滚")
    public Result<TccResultResponse> submit(@Valid @RequestBody TccOrderRequest request) {
        return Result.success(tccCoordinatorService.submit(request));
    }

    /**
     * 查询事务状态。
     */
    @GetMapping("/{xid}")
    @Operation(summary = "查询事务状态")
    public Result<Map<String, Object>> status(@PathVariable String xid) {
        return Result.success(tccCoordinatorService.status(xid));
    }

    /**
     * 查询事务的各分支记录。
     */
    @GetMapping("/{xid}/branches")
    @Operation(summary = "查询事务的各分支记录")
    public Result<List<TccBranch>> branches(@PathVariable String xid) {
        return Result.success(tccCoordinatorService.branches(xid));
    }

    /**
     * 手工触发恢复，处理停留在中间状态的事务。
     * 系统每 30 秒会自动执行一次，这里方便立即观察效果。
     */
    @PostMapping("/recover")
    @Operation(summary = "手工恢复停留在中间状态的事务")
    public Result<Integer> recover() {
        return Result.success(tccCoordinatorService.recoverPending());
    }

}
