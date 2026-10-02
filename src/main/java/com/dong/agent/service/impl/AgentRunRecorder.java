package com.dong.agent.service.impl;

import com.dong.agent.entity.AgentMessage;
import com.dong.agent.entity.AgentToolCall;
import com.dong.agent.enums.MessageRole;
import com.dong.agent.enums.ToolRisk;
import com.dong.agent.mapper.AgentMessageMapper;
import com.dong.agent.mapper.AgentToolCallMapper;
import com.dong.agent.support.AgentEventSink;
import com.dong.agent.support.AgentRunListener;
import com.dong.agent.support.RunOutcome;
import com.dong.agent.support.llm.ToolCall;
import com.dong.agent.support.tool.AgentTool;
import com.dong.agent.support.tool.ToolRegistry;
import com.dong.agent.support.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 运行记录器。把循环过程落成消息与工具调用记录，同时把事件推给出口。
 *
 * <p>不是 Spring bean：它持有一次运行的序号计数器与推送出口，
 * 做成单例会让两次运行互相污染。每次运行现场 new 一个。
 *
 * <p>落库不走长事务：一次运行可能持续几十秒，
 * 把整段循环包进事务会长时间占住连接（连接池只有 20 个）。
 * 每条消息各自写入，代价是宕机会留下半截运行，由清理任务收尾。
 */
@Slf4j
public class AgentRunRecorder implements AgentRunListener {

    /**
     * messageMapper，MyBatis Mapper 数据访问层。
     */
    private final AgentMessageMapper messageMapper;

    /**
     * toolCallMapper，MyBatis Mapper 数据访问层。
     */
    private final AgentToolCallMapper toolCallMapper;

    /**
     * 工具注册表，用于取工具的危险等级。
     */
    private final ToolRegistry toolRegistry;

    /**
     * 运行号。
     */
    private final String runNo;

    /**
     * 会话号。
     */
    private final String sessionNo;

    /**
     * 序号计数器。
     */
    private final AtomicInteger seq;

    /**
     * 事件出口，同步运行时为 null。
     */
    private final AgentEventSink sink;

    /**
     * 本次运行落库的消息数，用于累加会话计数。
     */
    private final AtomicInteger savedMessages = new AtomicInteger();

    /**
     * 构造记录器。
     *
     * @param messageMapper 消息 Mapper
     * @param toolCallMapper 工具调用 Mapper
     * @param toolRegistry  工具注册表
     * @param runNo         运行号
     * @param sessionNo     会话号
     * @param startSeq      起始序号
     * @param sink          事件出口，可为 null
     */
    public AgentRunRecorder(AgentMessageMapper messageMapper, AgentToolCallMapper toolCallMapper,
                            ToolRegistry toolRegistry, String runNo, String sessionNo, int startSeq, AgentEventSink sink) {
        this.messageMapper = messageMapper;
        this.toolCallMapper = toolCallMapper;
        this.toolRegistry = toolRegistry;
        this.runNo = runNo;
        this.sessionNo = sessionNo;
        this.seq = new AtomicInteger(startSeq);
        this.sink = sink;
    }

    /**
     * 运行开始。
     *
     * @param runNo     运行号
     * @param sessionNo 会话号
     * @param toolCount 本次可用的工具数
     */
    @Override
    public void onStart(String runNo, String sessionNo, int toolCount) {
        send("run.start", Map.of("runNo", runNo, "sessionNo", sessionNo, "toolCount", toolCount));
    }

    /**
     * 模型流式输出的一段正文。只推不落库，完整回答在收尾时落。
     *
     * @param delta 文本片段
     */
    @Override
    public void onMessageDelta(String delta) {
        send("message.delta", delta);
    }

    /**
     * 模型输出了一轮内容。只带工具调用时正文为空，落库时补一句描述，
     * 否则回放时看不出它这一步到底想干什么。
     *
     * @param step      第几步
     * @param content   正文
     * @param toolCalls 本次发起的工具调用
     */
    @Override
    public void onAssistantMessage(int step, String content, List<ToolCall> toolCalls) {
        String text = content == null || content.isBlank() ? describe(toolCalls) : content;
        saveMessage(MessageRole.ASSISTANT, text, "", "", false);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("step", step);
        data.put("content", text);
        send("assistant.message", data);
    }

    /**
     * 即将执行一个工具，只推事件，结果落库在 onToolResult。
     *
     * @param step      第几步
     * @param toolName  工具名
     * @param arguments 入参 JSON
     */
    @Override
    public void onToolCall(int step, String toolName, String arguments) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("step", step);
        data.put("toolName", toolName);
        data.put("arguments", arguments == null ? "" : arguments);
        send("tool.call", data);
    }

    /**
     * 工具执行完毕。成功与失败都要落库：失败不记的话，
     * 事后完全无法量化「有多少次失败被吞掉了」。
     *
     * @param step       第几步
     * @param toolName   工具名
     * @param toolCallId 调用 id
     * @param result     执行结果
     */
    @Override
    public void onToolResult(int step, String toolName, String toolCallId, ToolResult result) {
        AgentTool tool = toolRegistry.get(toolName);
        ToolRisk risk = tool == null ? ToolRisk.READ_ONLY : tool.risk();
        AgentToolCall record = new AgentToolCall();
        record.setRunNo(runNo);
        record.setSessionNo(sessionNo);
        record.setStepNo(step);
        record.setToolName(toolName);
        record.setArguments("");
        record.setResult(result.success() ? result.payload() : "");
        record.setStatus(result.success() ? 1 : 0);
        record.setErrorMessage(result.errorMessage());
        record.setRisk(risk);
        record.setElapsedMillis((int) result.elapsedMillis());
        toolCallMapper.insert(record);
        String content = result.success() ? result.payload() : result.errorMessage();
        saveMessage(MessageRole.TOOL, content, toolCallId, toolName, false);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("step", step);
        data.put("toolName", toolName);
        data.put("success", result.success());
        data.put("payload", result.success() ? result.payload() : "");
        data.put("errorMessage", result.errorMessage());
        data.put("elapsedMillis", result.elapsedMillis());
        send("tool.result", data);
    }

    /**
     * 运行结束，只推事件，收尾落库由运行服务负责。
     *
     * @param outcome 运行结局
     */
    @Override
    public void onFinish(RunOutcome outcome) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runNo", outcome.runNo());
        data.put("finishReason", outcome.finishReason().name());
        data.put("finishLabel", outcome.finishReason().getLabel());
        data.put("steps", outcome.steps());
        data.put("toolCalls", outcome.toolCalls());
        data.put("promptTokens", outcome.promptTokens());
        data.put("completionTokens", outcome.completionTokens());
        data.put("elapsedMillis", outcome.elapsedMillis());
        send("run.finish", data);
    }

    /**
     * 本次运行已落库的消息数。
     *
     * @return 消息数
     */
    public int savedMessages() {
        return savedMessages.get();
    }

    /**
     * 把工具调用列表描述成一句话，用于正文为空时补齐回放可读性。
     *
     * @param toolCalls 工具调用列表
     * @return 描述文本
     */
    private String describe(List<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder("调用工具：");
        for (int i = 0; i < toolCalls.size(); i++) {
            if (i > 0) {
                text.append("、");
            }
            text.append(toolCalls.get(i).name());
        }
        return text.toString();
    }

    /**
     * 落库一条消息。
     *
     * @param role       角色
     * @param content    内容
     * @param toolCallId 工具调用 id
     * @param toolName   工具名
     * @param truncated  是否截断
     */
    private void saveMessage(MessageRole role, String content, String toolCallId, String toolName, boolean truncated) {
        AgentMessage message = new AgentMessage();
        message.setSessionNo(sessionNo);
        message.setRunNo(runNo);
        message.setSeq(seq.getAndIncrement());
        message.setRole(role);
        message.setContent(content == null ? "" : content);
        message.setToolName(toolName);
        message.setToolCallId(toolCallId);
        message.setTruncated(truncated ? 1 : 0);
        messageMapper.insert(message);
        savedMessages.incrementAndGet();
    }

    /**
     * 推送事件。推送失败只记日志：页面断线不该影响运行继续落库。
     *
     * @param event 事件名
     * @param data  数据
     */
    private void send(String event, Object data) {
        if (sink == null) {
            return;
        }
        try {
            sink.send(event, data);
        } catch (Exception e) {
            log.warn("agent event send failed runNo={} event={}", runNo, event, e);
        }
    }

}
