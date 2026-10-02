package com.dong.agent.support.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工具结果的序列化与截断。
 *
 * <p>截断必须显式告诉模型「结果不完整」，否则它会基于半截数据下完整结论。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolJson {

    /**
     * 截断时追加的提示。
     */
    private static final String TRUNCATED_HINT = "...(结果已截断，需要更精确请用更窄的条件再查一次)";

    /**
     * JSON 序列化器。
     */
    private final ObjectMapper objectMapper;

    /**
     * 把任意结果对象序列化成 JSON 字符串。
     *
     * @param payload 结果对象
     * @return JSON 字符串，序列化失败时返回空字符串
     */
    public String write(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("agent tool result serialize failed type={}", payload == null ? "null" : payload.getClass().getSimpleName(), e);
            return "";
        }
    }

    /**
     * 序列化并按字符上限截断。
     *
     * @param payload  结果对象
     * @param maxChars 字符上限
     * @return 截断后的结果，超限时带提示后缀
     */
    public String writeTruncated(Object payload, int maxChars) {
        String json = write(payload);
        if (json.length() <= maxChars) {
            return json;
        }
        return json.substring(0, maxChars) + TRUNCATED_HINT;
    }

}
