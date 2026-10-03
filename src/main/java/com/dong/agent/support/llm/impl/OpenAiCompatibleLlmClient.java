package com.dong.agent.support.llm.impl;

import com.dong.agent.enums.MessageRole;
import com.dong.agent.support.TokenEstimator;
import com.dong.agent.support.llm.ChatMessage;
import com.dong.agent.support.llm.ChatRequest;
import com.dong.agent.support.llm.LlmClient;
import com.dong.agent.support.llm.LlmResult;
import com.dong.agent.support.llm.ToolCall;
import com.dong.agent.support.llm.ToolSpec;
import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenAI 兼容协议的模型客户端。DeepSeek / 通义 / Kimi / OpenAI 都能直接接。
 *
 * <p>用 JDK 自带的 HttpClient 而不是 WebClient：项目是 servlet 栈，
 * 引 webflux 会带来双栈与 reactor-netty，而这里只需要「发个请求、逐行读 SSE」。
 *
 * <p>三条硬性处理：
 * <ul>
 *   <li>流式正文逐段回推，拿到一段就推一段，不等整段生成完；</li>
 *   <li>工具调用是分片到达的（id、name、arguments 分多块），必须按 index 累积后合并；</li>
 *   <li>已经推出去的内容不能靠重试补——重试只在「一个字都没推」时进行，
 *       否则页面会看到两段接不上的正文。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleLlmClient implements LlmClient {

    /**
     * 连接超时。连接阶段的等待不该太长，连不上就快速失败。
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /**
     * 流式结束标记。
     */
    private static final String STREAM_DONE = "[DONE]";

    /**
     * JSON 序列化器。
     */
    private final ObjectMapper objectMapper;

    /**
     * HTTP 客户端。连接超时固定，读取超时按配置走请求级超时。
     */
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    /**
     * 接口地址，形如 https://api.deepseek.com/v1。
     */
    @Value("${dong.agent.llm.base-url:}")
    private String baseUrl;

    /**
     * 密钥。
     */
    @Value("${dong.agent.llm.api-key:}")
    private String apiKey;

    /**
     * 模型名。
     */
    @Value("${dong.agent.llm.model:}")
    private String model;

    /**
     * 采样温度。
     */
    @Value("${dong.agent.llm.temperature:0.2}")
    private double temperature;

    /**
     * 单次请求的整体超时，流式生成也受它约束。
     */
    @Value("${dong.agent.llm.read-timeout:60s}")
    private Duration readTimeout;

    /**
     * 最大重试次数，仅在尚未推出任何内容时生效。
     */
    @Value("${dong.agent.llm.max-retries:2}")
    private int maxRetries;

    /**
     * 获取提供方标识。
     *
     * @return 提供方标识
     */
    @Override
    public String provider() {
        return "openai";
    }

    /**
     * 发起一次流式对话。
     *
     * @param request 请求
     * @param onDelta 流式正文回调
     * @return 模型返回结果
     */
    @Override
    public LlmResult chat(ChatRequest request, Consumer<String> onDelta) {
        String endpoint = buildEndpoint();
        String body = buildBody(request);
        int promptTokens = TokenEstimator.estimate(body);
        StringBuilder content = new StringBuilder();
        Map<Integer, PendingTool> pending = new LinkedHashMap<>();
        boolean[] pushed = {false};
        for (int attempt = 1; attempt <= Math.max(1, maxRetries); attempt++) {
            try {
                content.setLength(0);
                pending.clear();
                if (pushed[0] && attempt > 1) {
                    // 已经推过正文就绝不重试，否则页面会看到两段接不上的内容
                    break;
                }
                stream(endpoint, body, text -> {
                    if (onDelta != null && text != null && !text.isEmpty()) {
                        onDelta.accept(text);
                        pushed[0] = true;
                    }
                    content.append(text == null ? "" : text);
                }, pending);
                List<ToolCall> toolCalls = new ArrayList<>();
                for (Map.Entry<Integer, PendingTool> entry : pending.entrySet()) {
                    PendingTool item = entry.getValue();
                    String id = item.id == null || item.id.isEmpty() ? "call_" + entry.getKey() : item.id;
                    toolCalls.add(new ToolCall(id, item.name, item.arguments.toString()));
                }
                int completionTokens = TokenEstimator.estimate(content.toString());
                return new LlmResult(content.toString(), toolCalls, promptTokens, completionTokens);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.warn("agent llm call failed attempt={}/{} model={}", attempt, maxRetries, model, e);
                if (pushed[0] || attempt == maxRetries) {
                    throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "llm call failed: " + e.getMessage());
                }
            }
        }
        throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "llm call failed after retries");
    }

    /**
     * 读取一次流式响应。
     *
     * @param endpoint 接口地址
     * @param body     请求体
     * @param onText   正文回调
     * @param pending  工具调用累积容器
     * @throws Exception 请求或读取失败
     */
    private void stream(String endpoint, String body, Consumer<String> onText, Map<Integer, PendingTool> pending) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(readTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            String detail = readFully(response.body());
            log.warn("agent llm bad status={} detail={}", response.statusCode(), detail);
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "llm returned status " + response.statusCode());
        }
        try (InputStream in = response.body();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty()) {
                    continue;
                }
                if (STREAM_DONE.equals(data)) {
                    break;
                }
                handleChunk(data, onText, pending);
            }
        }
    }

    /**
     * 处理一个流式分片。正文与工具调用可以同时出现，两者都要处理。
     *
     * @param data    分片内容
     * @param onText  正文回调
     * @param pending 工具调用累积容器
     */
    private void handleChunk(String data, Consumer<String> onText, Map<Integer, PendingTool> pending) {
        JsonNode root;
        try {
            root = objectMapper.readTree(data);
        } catch (Exception e) {
            log.warn("agent llm chunk parse failed, skipped");
            return;
        }
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode delta = choices.get(0).path("delta");
            JsonNode text = delta.path("content");
            if (!text.isMissingNode() && !text.isNull()) {
                onText.accept(text.asText());
            }
            JsonNode calls = delta.path("tool_calls");
            if (calls.isArray()) {
                for (JsonNode call : calls) {
                    int index = call.path("index").asInt();
                    PendingTool item = pending.computeIfAbsent(index, key -> new PendingTool());
                    if (!call.path("id").isMissingNode() && !call.path("id").isNull()) {
                        item.id = call.path("id").asText();
                    }
                    JsonNode function = call.path("function");
                    if (!function.isMissingNode()) {
                        if (!function.path("name").isMissingNode() && !function.path("name").isNull()) {
                            item.name = function.path("name").asText();
                        }
                        JsonNode arguments = function.path("arguments");
                        if (!arguments.isMissingNode() && !arguments.isNull()) {
                            item.arguments.append(arguments.asText());
                        }
                    }
                }
            }
        }
    }

    /**
     * 构造请求体。工具 schema 以 JSON 节点嵌入，不做二次字符串拼接。
     *
     * @param request 请求
     * @return 请求体 JSON
     */
    private String buildBody(ChatRequest request) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("stream", true);
        root.put("temperature", temperature);
        ArrayNode messages = root.putArray("messages");
        List<ChatMessage> list = request.messages() == null ? List.of() : request.messages();
        for (ChatMessage message : list) {
            messages.add(toMessageNode(message));
        }
        List<ToolSpec> tools = request.tools() == null ? List.of() : request.tools();
        if (!tools.isEmpty()) {
            ArrayNode toolNodes = root.putArray("tools");
            for (ToolSpec spec : tools) {
                ObjectNode toolNode = toolNodes.addObject();
                toolNode.put("type", "function");
                ObjectNode function = toolNode.putObject("function");
                function.put("name", spec.name());
                function.put("description", spec.description());
                try {
                    function.set("parameters", objectMapper.readTree(spec.parametersSchema()));
                } catch (Exception e) {
                    // schema 写错不能让整轮请求失败，退化成空对象由模型自由发挥参数
                    log.warn("agent tool schema invalid name={}", spec.name(), e);
                    function.putObject("parameters");
                }
            }
        }
        return root.toString();
    }

    /**
     * 把一条消息转成协议节点。
     *
     * @param message 消息
     * @return 协议节点
     */
    private ObjectNode toMessageNode(ChatMessage message) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", message.role() == null ? MessageRole.USER.getRole() : message.role().getRole());
        node.put("content", message.content() == null ? "" : message.content());
        if (message.role() == MessageRole.TOOL) {
            node.put("tool_call_id", message.toolCallId() == null ? "" : message.toolCallId());
            node.put("name", message.toolName() == null ? "" : message.toolName());
        }
        if (message.role() == MessageRole.ASSISTANT && message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            ArrayNode calls = node.putArray("tool_calls");
            for (ToolCall call : message.toolCalls()) {
                ObjectNode callNode = calls.addObject();
                callNode.put("id", call.id());
                callNode.put("type", "function");
                ObjectNode function = callNode.putObject("function");
                function.put("name", call.name());
                function.put("arguments", call.arguments() == null ? "" : call.arguments());
            }
        }
        return node;
    }

    /**
     * 拼接接口地址。base-url 末尾的斜杠要容忍，拼错会 404。
     *
     * @return 完整地址
     */
    private String buildEndpoint() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BusinessException(Constants.CODE_MIDDLEWARE_DISABLED, "agent llm base url is not configured");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(Constants.CODE_MIDDLEWARE_DISABLED, "agent api key is not configured");
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "/chat/completions";
    }

    /**
     * 读尽响应体，仅用于错误时取详情。
     *
     * @param in 响应流
     * @return 响应文本
     */
    private String readFully(InputStream in) {
        try (InputStream stream = in;
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null && text.length() < 512) {
                text.append(line);
            }
            return text.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 流式到达的工具调用分片。工具调用是分块来的：
     * 第一块给 id 与 name，后续块只给 arguments 的一小段，必须按 index 累积。
     */
    private static final class PendingTool {

        /**
         * 调用 id。
         */
        private String id;

        /**
         * 工具名。
         */
        private String name;

        /**
         * 入参累积。
         */
        private final StringBuilder arguments = new StringBuilder();

    }

}
