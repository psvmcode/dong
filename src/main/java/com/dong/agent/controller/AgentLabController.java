package com.dong.agent.controller;

import com.dong.agent.dto.LabResultQuery;
import com.dong.agent.dto.LabRunItem;
import com.dong.agent.dto.LabRunRequest;
import com.dong.agent.dto.LabRunResponse;
import com.dong.agent.service.AgentLabService;
import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.common.result.PageResult;
import com.dong.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 对照实验接口。八组实验覆盖工具注入、执行顺序、失败可见、上下文策略、
 * 自我校验、停止条件、幂等、并发隔离。
 *
 * <p>实验默认走 mock 模型，可复现、离线可跑、不烧钱；
 * 真实模型只在想观察「真实模型与剧本的偏差」时才用。
 */
@RestController
@Validated
@RequestMapping("/api/agent/lab")
@RequiredArgsConstructor
@Tag(name = "Agent 对照实验")
public class AgentLabController {

    /**
     * labService，业务服务层。
     */
    private final AgentLabService labService;

    /**
     * 模块开关。
     */
    @Value("${dong.agent.enabled:false}")
    private boolean enabled;

    /**
     * 模型提供方，mock 不需要 api-key。
     */
    @Value("${dong.agent.llm.provider:mock}")
    private String provider;

    /**
     * 模型密钥。
     */
    @Value("${dong.agent.llm.api-key:}")
    private String apiKey;

    /**
     * 跑一组对照实验，返回各模式的数字对比。
     */
    @PostMapping("/{key}")
    @Operation(summary = "跑一组对照实验，如 E2 串行与并行")
    public Result<LabRunResponse> run(@PathVariable @NotBlank @Size(max = 8) String key,
                                      @Valid @RequestBody LabRunRequest request) {
        checkAvailable();
        return Result.success(labService.run(key, request));
    }

    /**
     * 查询历史实验结果。
     */
    @PostMapping("/results")
    @Operation(summary = "查询历史实验结果，可按实验编号过滤")
    public Result<PageResult<LabRunItem>> results(@Valid @RequestBody LabResultQuery query) {
        checkAvailable();
        return Result.success(labService.results(query));
    }

    /**
     * 查询实验清单。
     */
    @PostMapping("/experiments")
    @Operation(summary = "查询可用的实验编号")
    public Result<List<String>> experiments() {
        checkAvailable();
        return Result.success(labService.experiments());
    }

    /**
     * 校验模块是否可用，与运行接口同一套判断。
     */
    private void checkAvailable() {
        if (!enabled) {
            throw new BusinessException(Constants.CODE_MIDDLEWARE_DISABLED, Constants.MESSAGE_MIDDLEWARE_DISABLED);
        }
        if (!"mock".equals(provider) && (apiKey == null || apiKey.isBlank())) {
            throw new BusinessException(Constants.CODE_MIDDLEWARE_DISABLED, "agent api key is not configured");
        }
    }

}
