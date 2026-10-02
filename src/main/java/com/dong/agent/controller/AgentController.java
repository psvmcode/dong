package com.dong.agent.controller;

import com.dong.agent.dto.MessageListQuery;
import com.dong.agent.dto.MessageResponse;
import com.dong.agent.dto.RunListQuery;
import com.dong.agent.dto.RunRequest;
import com.dong.agent.dto.RunResponse;
import com.dong.agent.dto.RunStatsResponse;
import com.dong.agent.dto.SessionCreateRequest;
import com.dong.agent.dto.SessionListQuery;
import com.dong.agent.dto.SessionResponse;
import com.dong.agent.dto.ToolDescriptor;
import com.dong.agent.dto.ToolDryRunRequest;
import com.dong.agent.dto.ToolDryRunResponse;
import com.dong.agent.service.AgentRunService;
import com.dong.agent.service.AgentSessionService;
import com.dong.agent.service.AgentToolService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 网页版 Agent。接口层只做参数校验与转发，循环与闸门全在 AgentRunEngine 里。
 *
 * <p>开关没开时统一返回 1004 而不是抛一堆连接异常：
 * 模块是可选的，缺配置不该让调用方分不清「没启用」和「模型挂了」。
 *
 * <p>推荐调用顺序：创建会话（或直接用 runs 自动建会话）→ 发起运行 → 查运行结果。
 * 页面走 /runs/stream，调试走 /runs（同步等结果）。
 */
@RestController
@Validated
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Tag(name = "网页版 Agent")
public class AgentController {

    /**
     * sessionService，业务服务层。
     */
    private final AgentSessionService sessionService;

    /**
     * runService，业务服务层。
     */
    private final AgentRunService runService;

    /**
     * toolService，业务服务层。
     */
    private final AgentToolService toolService;

    /**
     * 模块开关，默认关闭。
     */
    @Value("${dong.agent.enabled:false}")
    private boolean enabled;

    /**
     * 模型提供方，mock 不需要 api-key。
     */
    @Value("${dong.agent.llm.provider:mock}")
    private String provider;

    /**
     * 模型密钥，mock 模式下可以为空。
     */
    @Value("${dong.agent.llm.api-key:}")
    private String apiKey;

    /**
     * 创建会话。
     */
    @PostMapping("/sessions")
    @Operation(summary = "创建会话")
    public Result<String> createSession(@Valid @RequestBody SessionCreateRequest request) {
        checkAvailable();
        return Result.success(sessionService.create(request));
    }

    /**
     * 分页查询会话。
     */
    @PostMapping("/sessions/list")
    @Operation(summary = "分页查询会话")
    public Result<PageResult<SessionResponse>> listSessions(@Valid @RequestBody SessionListQuery query) {
        checkAvailable();
        return Result.success(sessionService.list(query));
    }

    /**
     * 查询会话详情。
     */
    @PostMapping("/sessions/{sessionNo}")
    @Operation(summary = "查询会话详情")
    public Result<SessionResponse> sessionDetail(@PathVariable @NotBlank @Size(max = 32) String sessionNo) {
        checkAvailable();
        return Result.success(sessionService.detail(sessionNo));
    }

    /**
     * 查询会话下的消息，含工具消息。
     */
    @PostMapping("/sessions/{sessionNo}/messages")
    @Operation(summary = "分页查询会话消息，含工具消息")
    public Result<PageResult<MessageResponse>> sessionMessages(@PathVariable @NotBlank @Size(max = 32) String sessionNo,
                                                               @Valid @RequestBody MessageListQuery query) {
        checkAvailable();
        return Result.success(sessionService.messages(sessionNo, query));
    }

    /**
     * 删除会话及其消息。
     */
    @DeleteMapping("/sessions/{sessionNo}")
    @Operation(summary = "删除会话及其消息与工具调用记录")
    public Result<Void> removeSession(@PathVariable @NotBlank @Size(max = 32) String sessionNo) {
        checkAvailable();
        sessionService.remove(sessionNo);
        return Result.success();
    }

    /**
     * 发起运行，同步等待结束。
     */
    @PostMapping("/runs")
    @Operation(summary = "发起一次运行，同步等待结束，调试与实验用")
    public Result<RunResponse> run(@Valid @RequestBody RunRequest request) {
        checkAvailable();
        return Result.success(runService.run(request));
    }

    /**
     * 发起运行，SSE 流式推送。页面用这条，返回的是事件流而不是统一响应。
     */
    @PostMapping(value = "/runs/stream", produces = "text/event-stream")
    @Operation(summary = "发起一次运行，SSE 流式推送过程")
    public SseEmitter stream(@Valid @RequestBody RunRequest request) {
        checkAvailable();
        return runService.stream(request);
    }

    /**
     * 查询运行结果。
     */
    @PostMapping("/runs/{runNo}")
    @Operation(summary = "查询运行结果")
    public Result<RunResponse> runDetail(@PathVariable @NotBlank @Size(max = 32) String runNo) {
        checkAvailable();
        return Result.success(runService.detail(runNo));
    }

    /**
     * 取消运行。
     */
    @PostMapping("/runs/{runNo}/cancel")
    @Operation(summary = "取消运行，由循环下一轮自己停下来")
    public Result<Void> cancelRun(@PathVariable @NotBlank @Size(max = 32) String runNo) {
        checkAvailable();
        runService.cancel(runNo);
        return Result.success();
    }

    /**
     * 分页查询运行。
     */
    @PostMapping("/runs/list")
    @Operation(summary = "分页查询运行")
    public Result<PageResult<RunResponse>> listRuns(@Valid @RequestBody RunListQuery query) {
        checkAvailable();
        return Result.success(runService.list(query));
    }

    /**
     * 查询运行统计。
     */
    @PostMapping("/runs/stats")
    @Operation(summary = "查询运行统计，含各结束原因的分布")
    public Result<RunStatsResponse> runStats() {
        checkAvailable();
        return Result.success(runService.stats());
    }

    /**
     * 查询工具清单。
     */
    @PostMapping("/tools")
    @Operation(summary = "查询工具清单，含描述、入参与危险等级")
    public Result<List<ToolDescriptor>> tools() {
        checkAvailable();
        return Result.success(toolService.list());
    }

    /**
     * 试运行只读工具。
     */
    @PostMapping("/tools/dry-run")
    @Operation(summary = "试运行只读工具，有副作用的工具不接受试运行")
    public Result<ToolDryRunResponse> dryRun(@Valid @RequestBody ToolDryRunRequest request) {
        checkAvailable();
        return Result.success(toolService.dryRun(request));
    }

    /**
     * 校验模块是否可用。缺 key 与没启用分开提示，
     * 否则调用方分不清「没启用」和「配好了但模型连不上」。
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
