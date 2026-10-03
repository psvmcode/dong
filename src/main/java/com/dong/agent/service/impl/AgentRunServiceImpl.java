package com.dong.agent.service.impl;

import com.dong.agent.dto.RunListQuery;
import com.dong.agent.dto.RunRequest;
import com.dong.agent.dto.RunResponse;
import com.dong.agent.dto.RunStatsResponse;
import com.dong.agent.entity.AgentMessage;
import com.dong.agent.entity.AgentRun;
import com.dong.agent.entity.AgentSession;
import com.dong.agent.enums.FinishReason;
import com.dong.agent.enums.MessageRole;
import com.dong.agent.enums.RunStatus;
import com.dong.agent.mapper.AgentMessageMapper;
import com.dong.agent.mapper.AgentRunMapper;
import com.dong.agent.mapper.AgentSessionMapper;
import com.dong.agent.mapper.AgentToolCallMapper;
import com.dong.agent.service.AgentRunService;
import com.dong.agent.service.AgentSessionService;
import com.dong.agent.support.AgentEventSink;
import com.dong.agent.support.AgentRunContext;
import com.dong.agent.support.AgentRunEngine;
import com.dong.agent.support.AgentRunOptions;
import com.dong.agent.support.AgentSummarySupport;
import com.dong.agent.support.RunOutcome;
import com.dong.agent.support.llm.ChatMessage;
import com.dong.agent.support.tool.ToolRegistry;
import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.common.result.PageRequest;
import com.dong.common.result.PageResult;
import com.dong.common.util.Snowflake;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 运行服务实现。
 *
 * <p>并发上限是这里最关键的一道闸门：一个 SSE 连接会长期占住一个 tomcat 工作线程，
 * 十几个并发会话就能让整个应用的所有接口无响应。超限时直接拒绝，
 * 比让应用整体不可用划算得多。
 */
@Slf4j
@Service
public class AgentRunServiceImpl implements AgentRunService {

    /**
     * 运行号前缀。
     */
    private static final String RUN_PREFIX = "AR";

    /**
     * 会话标题最大长度。
     */
    private static final int TITLE_LIMIT = 30;

    /**
     * runMapper，MyBatis Mapper 数据访问层。
     */
    private final AgentRunMapper runMapper;

    /**
     * sessionMapper，MyBatis Mapper 数据访问层。
     */
    private final AgentSessionMapper sessionMapper;

    /**
     * messageMapper，MyBatis Mapper 数据访问层。
     */
    private final AgentMessageMapper messageMapper;

    /**
     * toolCallMapper，MyBatis Mapper 数据访问层。
     */
    private final AgentToolCallMapper toolCallMapper;

    /**
     * sessionService，业务服务层。
     */
    private final AgentSessionService sessionService;

    /**
     * runEngine，运行引擎。
     */
    private final AgentRunEngine runEngine;

    /**
     * toolRegistry，工具注册表。
     */
    private final ToolRegistry toolRegistry;

    /**
     * snowflake，雪花发号器。
     */
    private final Snowflake snowflake;

    /**
     * summarySupport，会话摘要生成组件。
     */
    private final AgentSummarySupport summarySupport;

    /**
     * 运行调度线程池。
     */
    private final Executor runExecutor;

    /**
     * 当前并发运行数。
     */
    private final AtomicInteger activeRuns = new AtomicInteger();

    /**
     * 并发运行上限。
     */
    @Value("${dong.agent.max-concurrent-runs:8}")
    private int maxConcurrentRuns;

    /**
     * 进入模型的历史窗口条数。
     */
    @Value("${dong.agent.history-window:20}")
    private int historyWindow;

    /**
     * 单次运行墙钟超时。
     */
    @Value("${dong.agent.run-timeout:120s}")
    private Duration runTimeout;

    /**
     * 构造运行服务。
     *
     * @param runMapper      运行 Mapper
     * @param sessionMapper  会话 Mapper
     * @param messageMapper  消息 Mapper
     * @param toolCallMapper 工具调用 Mapper
     * @param sessionService 会话服务
     * @param runEngine      运行引擎
     * @param toolRegistry   工具注册表
     * @param snowflake      发号器
     * @param summarySupport 会话摘要组件
     * @param runExecutor    运行调度线程池
     */
    public AgentRunServiceImpl(AgentRunMapper runMapper, AgentSessionMapper sessionMapper,
                               AgentMessageMapper messageMapper, AgentToolCallMapper toolCallMapper,
                               AgentSessionService sessionService, AgentRunEngine runEngine,
                               ToolRegistry toolRegistry, Snowflake snowflake,
                               AgentSummarySupport summarySupport,
                               @Qualifier("agentRunExecutor") Executor runExecutor) {
        this.runMapper = runMapper;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.toolCallMapper = toolCallMapper;
        this.sessionService = sessionService;
        this.runEngine = runEngine;
        this.toolRegistry = toolRegistry;
        this.snowflake = snowflake;
        this.summarySupport = summarySupport;
        this.runExecutor = runExecutor;
    }

    /**
     * 发起运行，同步等待结束。
     *
     * @param request 运行请求
     * @return 运行结果
     */
    @Override
    public RunResponse run(RunRequest request) {
        return execute(request, null, null, true);
    }

    /**
     * 发起运行并绕过并发闸门，仅供实验使用。
     *
     * @param request 运行请求
     * @return 运行结果
     */
    @Override
    public RunResponse runUngated(RunRequest request) {
        return execute(request, null, null, false);
    }

    /**
     * 带实验参数发起运行。
     *
     * @param request       运行请求
     * @param options       实验参数覆盖
     * @param historyWindow 历史窗口覆盖
     * @return 运行结果
     */
    @Override
    public RunResponse runWithOptions(RunRequest request, AgentRunOptions options, Integer historyWindow) {
        return execute(request, options, historyWindow, true);
    }

    /**
     * 执行运行。正常跑、实验跑、绕闸门跑全都走这一段，
     * 实验比出来的才是参数本身的作用，而不是几套实现的差异。
     *
     * @param request       运行请求
     * @param options       实验参数覆盖，可为 null
     * @param historyWindow 历史窗口覆盖，可为 null
     * @param gated         是否受并发闸门约束
     * @return 运行结果
     */
    private RunResponse execute(RunRequest request, AgentRunOptions options, Integer historyWindow, boolean gated) {
        String sessionNo = sessionService.ensure(request.getSessionNo());
        String runNo = createRun(request, sessionNo);
        if (gated) {
            enterConcurrency();
        }
        try {
            List<ChatMessage> history = loadHistory(sessionNo, historyWindow);
            int startSeq = sessionService.nextSeq(sessionNo);
            saveUserMessage(sessionNo, runNo, startSeq, request.getPrompt());
            AgentRunRecorder recorder = new AgentRunRecorder(messageMapper, toolCallMapper, toolRegistry,
                    runNo, sessionNo, startSeq + 1, null);
            AgentRunContext context = buildContext(runNo, sessionNo, request, history);
            context.setOptions(options == null ? AgentRunOptions.empty() : options);
            RunOutcome outcome = runEngine.run(context, recorder);
            return finish(runNo, sessionNo, outcome, recorder.savedMessages() + 1);
        } finally {
            if (gated) {
                activeRuns.decrementAndGet();
            }
        }
    }

    /**
     * 发起运行，SSE 流式推送过程。
     *
     * @param request 运行请求
     * @return SSE 发射器
     */
    @Override
    public SseEmitter stream(RunRequest request) {
        String sessionNo = sessionService.ensure(request.getSessionNo());
        String runNo = createRun(request, sessionNo);
        enterConcurrency();
        List<ChatMessage> history = loadHistory(sessionNo);
        int startSeq = sessionService.nextSeq(sessionNo);
        saveUserMessage(sessionNo, runNo, startSeq, request.getPrompt());
        SseEmitter emitter = new SseEmitter(runTimeout.plusSeconds(10).toMillis());
        AgentEventSink sink = (event, data) -> emitter.send(SseEmitter.event().name(event).data(data));
        AgentRunRecorder recorder = new AgentRunRecorder(messageMapper, toolCallMapper, toolRegistry,
                runNo, sessionNo, startSeq + 1, sink);
        // 连接断开必须置取消标记：否则循环在后台继续跑，是最浪费的一种泄漏
        emitter.onCompletion(() -> markCancelled(runNo));
        emitter.onTimeout(() -> markCancelled(runNo));
        emitter.onError(e -> markCancelled(runNo));
        AgentRunContext context = buildContext(runNo, sessionNo, request, history);
        runExecutor.execute(() -> {
            try {
                RunOutcome outcome = runEngine.run(context, recorder);
                finish(runNo, sessionNo, outcome, recorder.savedMessages() + 1);
            } catch (Exception e) {
                log.error("agent stream run failed runNo={}", runNo, e);
                markFailed(runNo);
                try {
                    sink.send("error", Map.of("code", Constants.CODE_INTERNAL_ERROR, "message", "agent run failed"));
                } catch (Exception sendError) {
                    log.warn("agent error event send failed runNo={}", runNo, sendError);
                }
            } finally {
                activeRuns.decrementAndGet();
                emitter.complete();
            }
        });
        return emitter;
    }

    /**
     * 查询运行结果。
     *
     * @param runNo 运行号
     * @return 运行结果
     */
    @Override
    public RunResponse detail(String runNo) {
        return toResponse(requireRun(runNo));
    }

    /**
     * 取消运行。只置标记，由循环下一轮自己停下来，
     * 不去打断正在执行的工具——打断的代价是留下说不清的中间状态。
     *
     * @param runNo 运行号
     */
    @Override
    public void cancel(String runNo) {
        AgentRun run = requireRun(runNo);
        if (run.getStatus().isTerminal()) {
            return;
        }
        runMapper.updateStatus(runNo, RunStatus.CANCELLED);
        log.info("agent run cancelled runNo={}", runNo);
    }

    /**
     * 分页查询运行。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<RunResponse> list(RunListQuery query) {
        PageRequest page = query.toPageRequest();
        List<AgentRun> runs = runMapper.selectByPage(page.getOffset(), page.getPageSize());
        List<RunResponse> responses = runs.stream().map(this::toResponse).toList();
        return PageResult.of(responses, runMapper.countAll(), page);
    }

    /**
     * 运行统计。平均值只取最近一批记录在内存里算，
     * 全量聚合会把长尾运行的统计拖慢，而这里要的只是一个量级。
     *
     * @return 统计结果
     */
    @Override
    public RunStatsResponse stats() {
        RunStatsResponse response = new RunStatsResponse();
        response.setTotal(runMapper.countAll());
        Map<String, Long> counts = new LinkedHashMap<>();
        for (FinishReason reason : FinishReason.values()) {
            counts.put(reason.name(), runMapper.countByFinishReason(reason));
        }
        response.setFinishReasonCounts(counts);
        List<AgentRun> recent = runMapper.selectByPage(0, Constants.MAX_QUERY_LIMIT);
        if (recent.isEmpty()) {
            return response;
        }
        response.setAvgSteps(recent.stream().mapToInt(run -> run.getSteps() == null ? 0 : run.getSteps()).average().orElse(0.0));
        response.setAvgToolCalls(recent.stream().mapToInt(run -> run.getToolCalls() == null ? 0 : run.getToolCalls()).average().orElse(0.0));
        response.setAvgElapsedMillis(recent.stream().mapToInt(run -> run.getElapsedMillis() == null ? 0 : run.getElapsedMillis()).average().orElse(0.0));
        return response;
    }

    /**
     * 创建运行记录。幂等键命中时直接抛冲突，带上原运行号，
     * 让调用方拿到原单而不是重新跑一遍。
     *
     * @param request   运行请求
     * @param sessionNo 会话号
     * @return 运行号
     */
    private String createRun(RunRequest request, String sessionNo) {
        if (request.getClientToken() != null && !request.getClientToken().isBlank()) {
            AgentRun existing = runMapper.selectByClientToken(request.getClientToken());
            if (existing != null) {
                throw new BusinessException(Constants.CODE_IDEMPOTENT_REJECTED,
                        "duplicate request rejected, runNo=" + existing.getRunNo());
            }
        }
        AgentRun run = new AgentRun();
        run.setRunNo(RUN_PREFIX + snowflake.nextIdStr());
        run.setSessionNo(sessionNo);
        run.setClientToken(request.getClientToken() == null || request.getClientToken().isBlank()
                ? null : request.getClientToken());
        run.setPrompt(request.getPrompt());
        run.setStatus(RunStatus.RUNNING);
        runMapper.insert(run);
        return run.getRunNo();
    }

    /**
     * 占用一个并发名额，超出上限直接拒绝。
     */
    private void enterConcurrency() {
        if (activeRuns.incrementAndGet() > maxConcurrentRuns) {
            activeRuns.decrementAndGet();
            throw new BusinessException(Constants.CODE_TOO_MANY_REQUESTS, "too many concurrent agent runs");
        }
    }

    /**
     * 构造运行上下文。取消标记每轮都重新查库，
     * 用一次查库换「用户点了停止循环能立刻看到」。
     *
     * @param runNo     运行号
     * @param sessionNo 会话号
     * @param request   运行请求
     * @param history   历史消息
     * @return 运行上下文
     */
    private AgentRunContext buildContext(String runNo, String sessionNo, RunRequest request, List<ChatMessage> history) {
        AgentRunContext context = new AgentRunContext();
        context.setRunNo(runNo);
        context.setSessionNo(sessionNo);
        context.setPrompt(request.getPrompt());
        context.setHistory(history);
        context.setConfirmSideEffect(request.isConfirmSideEffect());
        context.setCancelChecker(() -> {
            AgentRun current = runMapper.selectByRunNo(runNo);
            return current != null && current.getStatus() == RunStatus.CANCELLED;
        });
        return context;
    }

    /**
     * 装载窗口内的历史与更早的摘要。
     *
     * @param sessionNo 会话号
     * @return 历史消息
     */
    private List<ChatMessage> loadHistory(String sessionNo) {
        return loadHistory(sessionNo, null);
    }

    /**
     * 装载历史，窗口可被实验覆盖。
     *
     * @param sessionNo     会话号
     * @param windowOverride 窗口覆盖，为空则用配置值
     * @return 历史消息
     */
    private List<ChatMessage> loadHistory(String sessionNo, Integer windowOverride) {
        int window = windowOverride == null || windowOverride < 1 ? historyWindow : windowOverride;
        List<ChatMessage> history = new ArrayList<>();
        AgentSession session = sessionMapper.selectBySessionNo(sessionNo);
        if (session != null && session.getSummary() != null && !session.getSummary().isBlank()) {
            history.add(ChatMessage.system("以下是更早的对话摘要：" + session.getSummary()));
        }
        Integer maxSeq = messageMapper.selectMaxSeq(sessionNo);
        int total = maxSeq == null ? 0 : maxSeq;
        int offset = Math.max(0, total - window);
        for (AgentMessage message : messageMapper.selectBySession(sessionNo, offset, window)) {
            ChatMessage converted = convert(message);
            if (converted != null) {
                history.add(converted);
            }
        }
        return history;
    }

    /**
     * 把库里的消息转回协议消息。
     *
     * @param message 消息实体
     * @return 协议消息，角色为空时返回 null
     */
    private ChatMessage convert(AgentMessage message) {
        MessageRole role = message.getRole();
        if (role == null) {
            return null;
        }
        String content = message.getContent() == null ? "" : message.getContent();
        return switch (role) {
            case SYSTEM -> ChatMessage.system(content);
            case USER -> ChatMessage.user(content);
            case ASSISTANT -> ChatMessage.assistant(content, List.of());
            case TOOL -> ChatMessage.tool(message.getToolCallId(), message.getToolName(), content);
        };
    }

    /**
     * 落库用户消息。
     *
     * @param sessionNo 会话号
     * @param runNo     运行号
     * @param seq       序号
     * @param prompt    用户输入
     */
    private void saveUserMessage(String sessionNo, String runNo, int seq, String prompt) {
        AgentMessage message = new AgentMessage();
        message.setSessionNo(sessionNo);
        message.setRunNo(runNo);
        message.setSeq(seq);
        message.setRole(MessageRole.USER);
        message.setContent(prompt);
        message.setToolName("");
        message.setToolCallId("");
        message.setTruncated(0);
        messageMapper.insert(message);
    }

    /**
     * 收尾：写回统计值、累加会话计数、首轮补标题。
     *
     * @param runNo      运行号
     * @param sessionNo  会话号
     * @param outcome    运行结局
     * @param messageDelta 本次新增消息数
     * @return 运行结果
     */
    private RunResponse finish(String runNo, String sessionNo, RunOutcome outcome, int messageDelta) {
        AgentRun run = runMapper.selectByRunNo(runNo);
        if (run == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "agent run not found " + runNo);
        }
        AgentSession session = sessionMapper.selectBySessionNo(sessionNo);
        if (session != null && (session.getTitle() == null || session.getTitle().isBlank())) {
            // 标题取用户的提问而不是模型的回答：列表里扫一眼想知道的是「这个会话在聊什么」
            sessionMapper.updateTitle(sessionNo, abbreviate(run.getPrompt()));
        }
        run.setStatus(outcome.finishReason() == FinishReason.ERROR ? RunStatus.FAILED : RunStatus.FINISHED);
        run.setFinishReason(outcome.finishReason());
        run.setAnswer(outcome.answer());
        run.setSteps(outcome.steps());
        run.setToolCalls(outcome.toolCalls());
        run.setPromptTokens(outcome.promptTokens());
        run.setCompletionTokens(outcome.completionTokens());
        run.setElapsedMillis((int) outcome.elapsedMillis());
        run.setErrorMessage("");
        runMapper.updateFinish(run);
        sessionMapper.increaseCounters(sessionNo, messageDelta, 1);
        refreshSummary(sessionNo);
        return toResponse(run);
    }

    /**
     * 会话消息超过窗口时才压缩历史。摘要失败只记日志：
     * 它只是让模型少看到一点过去，不该让会话变得不可用。
     *
     * @param sessionNo 会话号
     */
    private void refreshSummary(String sessionNo) {
        try {
            Integer maxSeq = messageMapper.selectMaxSeq(sessionNo);
            int total = maxSeq == null ? 0 : maxSeq;
            if (total <= historyWindow) {
                return;
            }
            List<AgentMessage> older = messageMapper.selectBySession(sessionNo, 0, total - historyWindow);
            List<ChatMessage> history = new ArrayList<>();
            for (AgentMessage message : older) {
                ChatMessage converted = convert(message);
                if (converted != null) {
                    history.add(converted);
                }
            }
            String summary = summarySupport.summarize(history);
            if (!summary.isEmpty()) {
                sessionMapper.updateSummary(sessionNo, summary);
            }
        } catch (Exception e) {
            log.warn("agent summary refresh failed sessionNo={}", sessionNo, e);
        }
    }

    /**
     * 标记运行失败。
     *
     * @param runNo 运行号
     */
    private void markFailed(String runNo) {
        AgentRun run = runMapper.selectByRunNo(runNo);
        if (run == null || run.getStatus().isTerminal()) {
            return;
        }
        run.setStatus(RunStatus.FAILED);
        run.setFinishReason(FinishReason.ERROR);
        run.setErrorMessage("agent run failed");
        runMapper.updateFinish(run);
    }

    /**
     * 标记运行取消，仅对未终结的运行生效。
     *
     * @param runNo 运行号
     */
    private void markCancelled(String runNo) {
        AgentRun run = runMapper.selectByRunNo(runNo);
        if (run == null || run.getStatus().isTerminal()) {
            return;
        }
        runMapper.updateStatus(runNo, RunStatus.CANCELLED);
    }

    /**
     * 按运行号查询，不存在则报数据不存在。
     *
     * @param runNo 运行号
     * @return 运行实体
     */
    private AgentRun requireRun(String runNo) {
        AgentRun run = runMapper.selectByRunNo(runNo);
        if (run == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "agent run not found " + runNo);
        }
        return run;
    }

    /**
     * 截取标题。
     *
     * @param text 原文
     * @return 截取后的标题
     */
    private String abbreviate(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String flat = text.replace("\n", " ").trim();
        return flat.length() > TITLE_LIMIT ? flat.substring(0, TITLE_LIMIT) + "..." : flat;
    }

    /**
     * 转换为响应对象。
     *
     * @param run 运行实体
     * @return 运行响应
     */
    private RunResponse toResponse(AgentRun run) {
        RunResponse response = new RunResponse();
        response.setRunNo(run.getRunNo());
        response.setSessionNo(run.getSessionNo());
        response.setStatus(run.getStatus() == null ? null : run.getStatus().getCode());
        response.setFinishReason(run.getFinishReason() == null ? "" : run.getFinishReason().name());
        response.setAnswer(run.getAnswer());
        response.setSteps(run.getSteps());
        response.setToolCalls(run.getToolCalls());
        response.setPromptTokens(run.getPromptTokens());
        response.setCompletionTokens(run.getCompletionTokens());
        response.setElapsedMillis(run.getElapsedMillis());
        response.setErrorMessage(run.getErrorMessage());
        response.setCreateTime(run.getCreateTime());
        return response;
    }

}
