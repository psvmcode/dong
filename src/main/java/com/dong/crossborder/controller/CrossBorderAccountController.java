package com.dong.crossborder.controller;

import com.dong.common.result.Result;
import com.dong.crossborder.dto.AccountCreateRequest;
import com.dong.crossborder.dto.AccountEventResponse;
import com.dong.crossborder.dto.AccountResponse;
import com.dong.crossborder.service.ComplianceService;
import com.dong.crossborder.service.CrossBorderAccountService;
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
import java.util.Map;
/**
 * 跨境账户与制裁名单。账户按币种分开，
 * 因为各币种资金分开清算，不能混在一个余额里。
 */
@RestController
@RequestMapping("/api/crossborder")
@RequiredArgsConstructor
@Tag(name = "跨境支付-账户")

public class CrossBorderAccountController {

    /**
     * accountService，业务服务层。
     */
    private final CrossBorderAccountService accountService;

    /**
     * complianceService，业务服务层。
     */
    private final ComplianceService complianceService;

    /**
     * 开户。kycLevel 决定可汇额度，这是各国监管的基本要求。
     */
    @PostMapping("/accounts")
    @Operation(summary = "开立跨境账户，返回账户 id")
    public Result<Long> create(@Valid @RequestBody AccountCreateRequest request) {
        return Result.success(accountService.create(request));
    }

    /**
     * 按账号查询。
     */
    @GetMapping("/accounts/{accountNo}")
    @Operation(summary = "按账号查询账户，含可用余额")
    public Result<AccountResponse> findByAccountNo(@PathVariable String accountNo) {
        return Result.success(accountService.findByAccountNo(accountNo));
    }

    /**
     * 查询全部账户。
     */
    @GetMapping("/accounts")
    @Operation(summary = "查询全部跨境账户")
    public Result<List<AccountResponse>> findAll() {
        return Result.success(accountService.findAll());
    }

    /**
     * 校验余额与流水是否一致。差额应为 0，不为 0 说明记账有遗漏。
     */
    @GetMapping("/accounts/{accountNo}/diff")
    @Operation(summary = "校验账户余额与流水的差额")
    public Result<Map<String, Object>> balanceDiff(@PathVariable String accountNo,
                                                   @RequestParam(defaultValue = "0") double initial) {
        AccountResponse account = accountService.findByAccountNo(accountNo);
        java.math.BigDecimal diff = accountService.balanceDiff(account.getId(),
                new java.math.BigDecimal(initial));
        return Result.success(Map.of(
                "accountNo", accountNo,
                "balance", account.getBalance(),
                "diff", diff,
                "consistent", diff.compareTo(java.math.BigDecimal.ZERO) == 0));
    }

    /**
     * 冻结账户。反洗钱调查或司法冻结时使用，冻结后不能发起新汇款，
     * 余额与流水完整保留。冻结动作与原因都会落事件表留痕。
     */
    @PostMapping("/accounts/{accountNo}/freeze")
    @Operation(summary = "冻结账户，事件落库留痕")
    public Result<AccountResponse> freeze(@PathVariable String accountNo,
                                          @RequestParam(defaultValue = "") String reason,
                                          @RequestParam(defaultValue = "") String operator) {
        return Result.success(accountService.freeze(accountNo, reason, operator));
    }

    /**
     * 解冻账户。与冻结对称，同样落事件留痕。
     */
    @PostMapping("/accounts/{accountNo}/unfreeze")
    @Operation(summary = "解冻账户，事件落库留痕")
    public Result<AccountResponse> unfreeze(@PathVariable String accountNo,
                                            @RequestParam(defaultValue = "") String reason,
                                            @RequestParam(defaultValue = "") String operator) {
        return Result.success(accountService.unfreeze(accountNo, reason, operator));
    }

    /**
     * 查询账户事件历史。谁在什么时间因为什么冻结或解冻了账户，逐条可查。
     */
    @GetMapping("/accounts/{accountNo}/events")
    @Operation(summary = "查询账户冻结/解冻事件历史")
    public Result<List<AccountEventResponse>> events(@PathVariable String accountNo) {
        return Result.success(accountService.events(accountNo));
    }

    /**
     * 加入制裁名单。命中名单的汇款会被直接拒绝。
     */
    @PostMapping("/sanction")
    @Operation(summary = "加入制裁名单")
    public Result<Void> addSanction(@RequestParam String ownerName) {
        complianceService.addSanction(ownerName);
        return Result.success();
    }

    /**
     * 从制裁名单移除。
     */
    @DeleteMapping("/sanction")
    @Operation(summary = "从制裁名单移除")
    public Result<Void> removeSanction(@RequestParam String ownerName) {
        complianceService.removeSanction(ownerName);
        return Result.success();
    }

    /**
     * 查询名单大小。
     */
    @GetMapping("/sanction/count")
    @Operation(summary = "查询制裁名单大小")
    public Result<Long> sanctionCount() {
        return Result.success(complianceService.sanctionCount());
    }

}
