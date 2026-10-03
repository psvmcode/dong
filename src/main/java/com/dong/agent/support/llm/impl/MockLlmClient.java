package com.dong.agent.support.llm.impl;

import com.dong.agent.enums.MessageRole;
import com.dong.agent.support.TokenEstimator;
import com.dong.agent.support.llm.ChatMessage;
import com.dong.agent.support.llm.ChatRequest;
import com.dong.agent.support.llm.LlmClient;
import com.dong.agent.support.llm.LlmResult;
import com.dong.agent.support.llm.ToolCall;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 剧本化模型。按预设规则返回，不联网、不烧钱、结果可复现。
 *
 * <p>它是实验能跑起来的前提：真实模型有随机性，同一组对照实验两次跑出来数字不一样，
 * 混在一起对比会得出错误结论。默认 provider 就是它，真实模型只用于观察偏差。
 *
 * <p>剧本只有三幕：
 * 用户问时间 → 调 time.now；拿到工具结果 → 基于结果回答；其余 → 固定回复。
 * 用来验证整条链路（流式、工具调用、结果回填、再回答）是否通。
 */
@Slf4j
@Component
public class MockLlmClient implements LlmClient {

    /**
     * 触发时间工具的关键词。
     */
    private static final List<String> TIME_KEYWORDS = List.of("几点", "时间", "几号", "日期", "现在", "今天");

    /**
     * 每次推流的字符数，模拟真实的逐段输出。
     */
    private static final int CHUNK_SIZE = 6;

    /**
     * 每段之间的间隔，单位毫秒。
     */
    private static final long CHUNK_SLEEP_MILLIS = 5L;

    /**
     * 单条内容回传模型时的最大长度，避免把长工具结果整段塞回 prompt。
     */
    private static final int QUOTE_LIMIT = 200;

    /**
     * 获取提供方标识。
     *
     * @return 提供方标识
     */
    @Override
    public String provider() {
        return "mock";
    }

    /**
     * 按剧本返回。最后一条是工具结果就收尾回答，是用户提问就决定要不要调工具。
     *
     * @param request 请求
     * @param onDelta 流式正文回调
     * @return 模型返回结果
     */
    @Override
    public LlmResult chat(ChatRequest request, Consumer<String> onDelta) {
        List<ChatMessage> messages = request.messages() == null ? List.of() : request.messages();
        int promptTokens = estimatePrompt(request);
        ChatMessage last = messages.isEmpty() ? null : messages.get(messages.size() - 1);
        // 剧本判定一律基于最后一条用户提问，而不是最后一条消息：
        // 第二轮之后最后一条是工具结果，用它判断的话剧本在第一轮之后就失效了
        String question = lastUserQuestion(messages);
        if (isLoopQuestion(question)) {
            // 摇摆剧本：交替调用两个不同工具，永远不收敛。
            // 它是 E6 的素材——没有闸门就会一直跑下去
            long toolMessages = messages.stream().filter(m -> m.role() == MessageRole.TOOL).count();
            String thought = "再确认一次，第 " + (toolMessages + 1) + " 轮。";
            stream(thought, onDelta);
            ToolCall call = toolMessages % 2 == 0
                    ? new ToolCall("call_" + (toolMessages + 1), "time.now", "{}")
                    : new ToolCall("call_" + (toolMessages + 1), "math.calc", "{\"expression\": \"1 + 1\"}");
            return new LlmResult(thought, List.of(call), promptTokens, TokenEstimator.estimate(thought));
        }
        if (last != null && last.role() == MessageRole.TOOL) {
            String answer = "（mock）工具 " + last.toolName() + " 返回：" + trim(last.content())
                    + "。以上结论来自工具返回值，不是推测。";
            stream(answer, onDelta);
            return new LlmResult(answer, List.of(), promptTokens, TokenEstimator.estimate(answer));
        }
        if (isFailureQuestion(question)) {
            // 故意调一个不存在的工具，为 E3 制造一次真实失败
            String thought = "我先查一下，调用 not.exist。";
            stream(thought, onDelta);
            ToolCall call = new ToolCall("call_bad", "not.exist", "{}");
            return new LlmResult(thought, List.of(call), promptTokens, TokenEstimator.estimate(thought));
        }
        if (isTimeQuestion(question)) {
            String thought = "我需要确认两个时区的当前时间，一次调用两个工具。";
            stream(thought, onDelta);
            // 刻意返回两个互不依赖的调用，用来验证并行执行与按原始顺序回填
            List<ToolCall> calls = List.of(
                    new ToolCall("call_1", "time.now", "{\"zone\": \"Asia/Shanghai\"}"),
                    new ToolCall("call_2", "time.now", "{\"zone\": \"UTC\"}"));
            return new LlmResult(thought, calls, promptTokens, TokenEstimator.estimate(thought));
        }
        String answer = "（mock 模型）当前未接入真实模型，这是一段剧本化的固定回复。你的输入是：" + trim(question);
        stream(answer, onDelta);
        return new LlmResult(answer, List.of(), promptTokens, TokenEstimator.estimate(answer));
    }

    /**
     * 取最后一条用户提问。剧本判定只能基于它，
     * 基于最后一条消息会在第一轮之后就失效。
     *
     * @param messages 完整历史
     * @return 最后一条用户提问，没有时返回空串
     */
    private String lastUserQuestion(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message.role() == MessageRole.USER) {
                return message.content() == null ? "" : message.content();
            }
        }
        return "";
    }

    /**
     * 判断是否要制造一次工具失败。
     *
     * @param question 用户输入
     * @return true 表示调用一个不存在的工具
     */
    private boolean isFailureQuestion(String question) {
        return question != null && question.contains("失败");
    }

    /**
     * 判断是否要跑摇摆剧本。
     *
     * @param question 用户输入
     * @return true 表示进入永不收敛的循环
     */
    private boolean isLoopQuestion(String question) {
        return question != null && (question.contains("摇摆") || question.contains("循环") || question.contains("卡住"));
    }

    /**
     * 判断是否与时间相关。
     *
     * @param question 用户输入
     * @return true 表示应当调用时间工具
     */
    private boolean isTimeQuestion(String question) {
        if (question == null) {
            return false;
        }
        for (String keyword : TIME_KEYWORDS) {
            if (question.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 估算提示部分的 token。
     *
     * @param request 请求
     * @return 估算的提示 token 数
     */
    private int estimatePrompt(ChatRequest request) {
        StringBuilder text = new StringBuilder();
        for (ChatMessage message : request.messages()) {
            if (message.content() != null) {
                text.append(message.content());
            }
        }
        for (var spec : request.tools()) {
            text.append(spec.name()).append(spec.description()).append(spec.parametersSchema());
        }
        return TokenEstimator.estimate(text.toString());
    }

    /**
     * 分段推送正文，模拟流式输出。中断时恢复中断标记并停止推送。
     *
     * @param content 正文
     * @param onDelta 回调，可能为 null
     */
    private void stream(String content, Consumer<String> onDelta) {
        if (onDelta == null || content == null || content.isEmpty()) {
            return;
        }
        List<String> chunks = new ArrayList<>();
        for (int index = 0; index < content.length(); index += CHUNK_SIZE) {
            chunks.add(content.substring(index, Math.min(index + CHUNK_SIZE, content.length())));
        }
        for (String chunk : chunks) {
            onDelta.accept(chunk);
            try {
                Thread.sleep(CHUNK_SLEEP_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * 截断长文本，避免把整段工具结果原样回传。
     *
     * @param text 原文
     * @return 截断后的文本
     */
    private String trim(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replace("\n", " ");
        return flat.length() > QUOTE_LIMIT ? flat.substring(0, QUOTE_LIMIT) + "..." : flat;
    }

}
