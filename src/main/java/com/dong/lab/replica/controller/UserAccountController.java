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

/**
 * 第二数据源账户。数据源、会话工厂和事务管理器都与主库独立，
 * 用来演示多数据源配置以及跨库事务的边界。
 *
 * <p>注意这里用 ObjectProvider 而不是直接注入：
 * MariaDB 默认关闭，关闭时容器里没有对应 service bean，直接注入会启动失败。
 */
@RestController
@RequestMapping("/api/replica/accounts")
@RequiredArgsConstructor
@Tag(name = "多数据源-账户")
public class UserAccountController {

    private final ObjectProvider<UserAccountService> userAccountServiceProvider;

    /**
     * 创建账户。
     */
    @PostMapping
    @Operation(summary = "创建账户，写入第二数据源")
    public Result<Long> create(@RequestParam Long userId,
                               @RequestParam(defaultValue = "") String username,
                               @RequestParam(defaultValue = "0") long balance) {
        return Result.success(requireService().create(userId, username, balance));
    }

    /**
     * 按用户 id 查询账户。
     */
    @GetMapping
    @Operation(summary = "按用户 id 查询账户")
    public Result<UserAccount> findByUserId(@RequestParam Long userId) {
        return Result.success(requireService().findByUserId(userId));
    }

    /**
     * 查询全部账户。
     */
    @GetMapping("/all")
    @Operation(summary = "查询全部账户")
    public Result<List<UserAccount>> findAll() {
        return Result.success(requireService().findAll());
    }

    /**
     * 两个账户之间转账，在单个本地事务内完成。
     * 跨库场景这里无法保证，只能各自本地事务，需要分布式事务兜底。
     */
    @PostMapping("/transfer")
    @Operation(summary = "两个账户之间转账，在单个本地事务内完成")
    public Result<Long> transfer(@RequestParam Long fromUserId,
                                 @RequestParam Long toUserId,
                                 @RequestParam long amount) {
        return Result.success(requireService().transfer(fromUserId, toUserId, amount));
    }

    /**
     * 连读两次观察是否有延迟。读写分离场景下这个接口能暴露主从延迟问题。
     */
    @GetMapping("/consistency")
    @Operation(summary = "连续读取两次，观察是否存在延迟")
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
