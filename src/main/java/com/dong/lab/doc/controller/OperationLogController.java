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

@RestController
@RequestMapping("/api/doc/operation-log")
@RequiredArgsConstructor
@Tag(name = "doc-operation-log")
public class OperationLogController {

    private final ObjectProvider<OperationLogService> operationLogServiceProvider;

    @PostMapping
    @Operation(summary = "write a schemaless log document into mongodb")
    public Result<String> save(@Valid @RequestBody OperationLogRequest request) {
        return Result.success(requireService().save(request));
    }

    @GetMapping
    public Result<PageResult<OperationLogDocument>> findByPage(@RequestParam(required = false) String bizType,
                                                               @RequestParam(defaultValue = "1") int pageNum,
                                                               @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(requireService().findByPage(bizType, pageNum, pageSize));
    }

    @GetMapping("/count")
    public Result<Long> count() {
        return Result.success(requireService().count());
    }

    private OperationLogService requireService() {
        OperationLogService service = operationLogServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BusinessException(Constants.CODE_MIDDLEWARE_DISABLED, "set lab.mongodb.enabled=true first");
        }
        return service;
    }

}
