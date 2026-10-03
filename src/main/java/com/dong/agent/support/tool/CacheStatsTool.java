package com.dong.agent.support.tool;

import com.dong.agent.enums.ToolRisk;
import com.dong.framework.cache.CacheStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 缓存统计查询工具。
 *
 * <p>统计数据来自 framework 层的 CacheStats 组件而不是业务 service：
 * 各级命中、降级、熔断计数本来就在那里攒着，没必要再包一层。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheStatsTool implements AgentTool {

    /**
     * cacheStats，缓存统计组件。
     */
    private final CacheStats cacheStats;

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
        return "cache.stats";
    }

    /**
     * 获取工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String description() {
        return "查看多级缓存的命中率与各类降级计数，含命中、穿透拦截、熔断、旧值兜底。"
                + "当用户问缓存效果、命中率、有没有在降级时调用；"
                + "想跑一次穿透实验应该用 cache.penetration_lab，而不是这个工具。";
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
     * 读取缓存统计。
     *
     * @param arguments 入参，无
     * @return 执行结果
     */
    @Override
    public ToolResult invoke(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        try {
            return ToolResult.ok(toolJson.write(cacheStats.snapshot()), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("agent tool cache.stats failed", e);
            return ToolResult.fail("读取缓存统计失败：" + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

}
