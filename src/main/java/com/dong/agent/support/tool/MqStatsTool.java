package com.dong.agent.support.tool;

import com.dong.agent.enums.ToolRisk;
import com.dong.mq.service.MqConsumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 消息消费统计工具。含重复投递计数，能直接看出幂等有没有生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqStatsTool implements AgentTool {

    /**
     * mqConsumeService，业务服务层。
     */
    private final MqConsumeService mqConsumeService;

    /**
     * toolJson，工具结果的序列化与截断。
     */
    private final ToolJson toolJson;

    /**
     * 获取工具名。
     *
     * @return 工具名
     */
    @Override
    public String name() {
        return "mq.stats";
    }

    /**
     * 获取工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String description() {
        return "查看消息消费统计，含投递总数、重复投递次数与当前生效的传输实现。"
                + "当用户问消息投递情况、重复消费有没有被拦住时调用；"
                + "要发消息时不要用这个工具。";
    }

    /**
     * 获取入参的 JSON Schema。
     *
     * @return JSON Schema 字符串
     */
    @Override
    public String parametersSchema() {
        return """
                {"type": "object", "properties": {}, "required": []}""";
    }

    /**
     * 获取危险等级。
     *
     * @return 危险等级
     */
    @Override
    public ToolRisk risk() {
        return ToolRisk.READ_ONLY;
    }

    /**
     * 读取消息统计。
     *
     * @param arguments 入参，无
     * @return 执行结果
     */
    @Override
    public ToolResult invoke(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        try {
            return ToolResult.ok(toolJson.write(mqConsumeService.stats()), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("agent tool mq.stats failed", e);
            return ToolResult.fail("读取消息统计失败：" + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

}
