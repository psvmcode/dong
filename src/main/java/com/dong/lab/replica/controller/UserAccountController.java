package com.dong.lab.replica.controller;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import com.dong.lab.common.result.Result;
import com.dong.lab.replica.entity.UserAccount;
import com.dong.lab.replica.service.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/replica/accounts")
@RequiredArgsConstructor
@Tag(name = "replica-account")
public class UserAccountController {

    private final ObjectProvider<UserAccountService> userAccountServiceProvider;

    @PostMapping
    public Result<Long> create(@RequestParam Long userId,
                               @RequestParam(defaultValue = "") String username,
                               @RequestParam(defaultValue = "0") long balance) {
        return Result.success(requireService().create(userId, username, balance));
    }

    @GetMapping
    public Result<UserAccount> findByUserId(@RequestParam Long userId) {
        return Result.success(requireService().findByUserId(userId));
    }

    @GetMapping("/all")
    public Result<List<UserAccount>> findAll() {
        return Result.success(requireService().findAll());
    }

    @PostMapping("/transfer")
    @Operation(summary = "transfer between two accounts on the second database instance")
    public Result<Long> transfer(@RequestParam Long fromUserId,
                                 @RequestParam Long toUserId,
                                 @RequestParam long amount) {
        return Result.success(requireService().transfer(fromUserId, toUserId, amount));
    }

    @GetMapping("/consistency")
    @Operation(summary = "read twice to observe replication lag")
    public Result<Map<String, Object>> consistency(@RequestParam Long userId) {
        return Result.success(requireService().consistencyCheck(userId));
    }

    private UserAccountService requireService() {
        UserAccountService service = userAccountServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BusinessException(Constants.CODE_MIDDLEWARE_DISABLED, "set lab.mariadb.enabled=true first");
        }
        return service;
    }

}
