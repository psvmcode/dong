package com.dong.agent.support;

import com.dong.agent.support.llm.ChatMessage;
import com.dong.agent.support.llm.ChatRequest;
import com.dong.agent.support.llm.LlmClient;
import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 会话摘要生成。把窗口之外的历史压成一段话，作为系统提示的一部分带上。
 *
 * <p>无状态组件：只负责把历史变成摘要文本，写库由调用方做。
 *
 * <p>失败一律降级返回空串并记日志：摘要生成不出来只是让模型少看到一点历史，
 * 不该让会话变得不可用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentSummarySupport {

    /**
     * 摘要指令。要求只输出摘要本身，否则模型会先寒暄两句再给结论。
     */
    private static final String INSTRUCTION = """
            下面是一段更早的对话。请用不超过 200 字概括：用户在关心什么、
            已经确认过哪些结论、还有哪些问题没解决。
            只输出摘要，不要加任何开场白。""";

    /**
     * 参与摘要的历史条数上限。再多的历史也压不出更多信息，只会拖慢。
     */
    private static final int MAX_MESSAGES = 40;

    /**
     * 模型客户端集合，按 provider 选择。
     */
    private final Map<String, LlmClient> clients;

    /**
     * 模型提供方。
     */
    @Value("${dong.agent.llm.provider:mock}")
    private String provider;

    /**
     * 摘要最大长度。
     */
    @Value("${dong.agent.summary-max-chars:800}")
    private int maxChars;

    /**
     * 生成摘要。
     *
     * @param older 窗口之外的历史消息
     * @return 摘要文本，失败或无需生成时返回空串
     */
    public String summarize(List<ChatMessage> older) {
        if (older == null || older.isEmpty()) {
            return "";
        }
        List<ChatMessage> picked = older.size() > MAX_MESSAGES ? older.subList(0, MAX_MESSAGES) : older;
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(INSTRUCTION));
        messages.addAll(picked);
        messages.add(ChatMessage.user("请概括上面这段对话。"));
        try {
            String content = client().chat(new ChatRequest(messages, List.of()), null).content();
            if (content == null || content.isBlank()) {
                return "";
            }
            String flat = content.replace("\n", " ").trim();
            return flat.length() > maxChars ? flat.substring(0, maxChars) : flat;
        } catch (Exception e) {
            log.warn("agent summary generation failed provider={}", provider, e);
            return "";
        }
    }

    /**
     * 按配置的 provider 取模型客户端。
     *
     * @return 模型客户端
     */
    private LlmClient client() {
        for (LlmClient client : clients.values()) {
            if (client.provider().equals(provider)) {
                return client;
            }
        }
        throw new BusinessException(Constants.CODE_MIDDLEWARE_DISABLED, "llm provider not available: " + provider);
    }

}
