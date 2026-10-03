package com.dong.agent.support.tool;

import com.dong.agent.enums.ToolRisk;
import com.dong.classic.service.RateLimitLabService;
import com.dong.common.constant.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * 限流四算法对比工具。有副作用：它会真的往 Redis 打一批请求并写入实验结果表。
 *
 * <p>默认参数刻意取小（30 次尝试）：对比实验的目的是看出算法差异，
 * 不是压测，把 attempts 开到几百只会把远程 Redis 拖慢，结论反而失真。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitCompareTool implements AgentTool {

    /**
     * 本工具超时，比默认长：要跑两轮突发。
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(40);

    /**
     * 尝试次数上限。
     */
    private static final int MAX_ATTEMPTS = 200;

    /**
     * rateLimitLabService，业务服务层。
     */
    private final RateLimitLabService rateLimitLabService;

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
        return "classic.limiter_compare";
    }

    /**
     * 获取工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String description() {
        return "对比固定窗口、滑动窗口、令牌桶、漏桶四种限流算法在两轮突发下的放行数量，需要用户确认后执行。"
                + "当用户问这四种算法有什么区别时调用；"
                + "只是想看历史结论用 classic.lab_record，不必重跑。";
    }

    /**
     * 获取入参的 JSON Schema。
     *
     * @return JSON Schema 字符串
     */
    @Override
    public String parametersSchema() {
        return """
                {"type": "object", "properties": {"limit": {"type": "integer",
                "description": "窗口内配额，默认 10"}, "windowSeconds": {"type": "integer",
                "description": "窗口秒数，默认 6"}, "attempts": {"type": "integer",
                "description": "每轮尝试次数，默认 30，最多 200"}, "gapMillis": {"type": "integer",
                "description": "两轮之间的间隔毫秒，默认 3500，给 0 看不出算法差异"}}, "required": []}""";
    }

    /**
     * 获取危险等级。
     *
     * @return 危险等级
     */
    @Override
    public ToolRisk risk() {
        return ToolRisk.SIDE_EFFECT;
    }

    /**
     * 获取超时。
     *
     * @return 超时时长
     */
    @Override
    public Duration timeout() {
        return TIMEOUT;
    }

    /**
     * 跑一次限流对比。
     *
     * @param arguments 入参
     * @return 执行结果
     */
    @Override
    public ToolResult invoke(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        String bizKey = ToolArguments.stringIn(arguments, "bizKey", "agent-demo", 64);
        long limit = ToolArguments.intIn(arguments, "limit", 10, 1, Constants.MAX_BATCH_SIZE);
        long windowSeconds = ToolArguments.intIn(arguments, "windowSeconds", 6, 1, (int) Constants.MAX_WINDOW_SECONDS);
        int attempts = ToolArguments.intIn(arguments, "attempts", 30, 1, MAX_ATTEMPTS);
        long gapMillis = ToolArguments.intIn(arguments, "gapMillis", 3_500, 0, 60_000);
        boolean distributed = ToolArguments.booleanIn(arguments, "distributed", true);
        try {
            Map<String, Object> result = rateLimitLabService.compare(bizKey, limit, windowSeconds, attempts, distributed, gapMillis);
            return ToolResult.ok(toolJson.writeTruncated(result, maxResultChars()), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("agent tool classic.limiter_compare failed bizKey={}", bizKey, e);
            return ToolResult.fail("限流对比执行失败：" + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

}
