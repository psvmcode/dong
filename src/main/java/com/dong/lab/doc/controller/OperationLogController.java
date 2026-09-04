package com.dong.lab.doc.controller;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import com.dong.lab.common.result.PageResult;
import com.dong.lab.common.result.Result;
import com.dong.lab.doc.dto.OperationLogRequest;
import com.dong.lab.doc.entity.OperationLogDocument;
import com.dong.lab.doc.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
/**
 * 操作日志。用 MongoDB 存储，因为日志字段会随业务不断演进，
 * 无 schema 的特性避免了每次加字段都要改表结构。
 */
@RestController
@RequestMapping("/api/doc/operation-log")
@RequiredArgsConstructor
@Tag(name = "文档-操作日志")

public class OperationLogController {

    // ObjectProvider 是因为 MongoDB 默认关闭，关闭时容器里没有对应 bean
    /**
     * operationLogServiceProvider，缓存提供者。
     */
    private final ObjectProvider<OperationLogService> operationLogServiceProvider;

    /**
     * 写入一条日志。detail 字段是任意对象，不同业务可以写入完全不同的结构。
     */
    @PostMapping
    @Operation(summary = "写入一条无 schema 的操作日志")
    public Result<String> save(@Valid @RequestBody OperationLogRequest request) {
        return Result.success(requireService().save(request));
    }

    /**
     * 按业务类型分页查询。
     */
    @GetMapping
    @Operation(summary = "按业务类型分页查询操作日志")
    public Result<PageResult<OperationLogDocument>> findByPage(@RequestParam(required = false) String bizType,
                                                               @RequestParam(defaultValue = "1") int pageNum,
                                                               @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(requireService().findByPage(bizType, pageNum, pageSize));
    }

    /**
     * 查询日志总条数。
     */
    @GetMapping("/count")
    @Operation(summary = "查询操作日志总条数")
    public Result<Long> count() {
        return Result.success(requireService().count());
    }

    /**
     * requireService。
     */
    private OperationLogService requireService() {
        OperationLogService service = operationLogServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BusinessException(Constants.CODE_MIDDLEWARE_DISABLED, "set lab.mongodb.enabled=true first");
        }
        return service;
    }

}
