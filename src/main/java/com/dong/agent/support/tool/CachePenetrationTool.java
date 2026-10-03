package com.dong.agent.support.tool;

import com.dong.agent.enums.ToolRisk;
import com.dong.cache.service.CacheLabService;
import com.dong.common.constant.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * 缓存穿透实验工具。有副作用：会真的往缓存与数据库打一批不存在的 id。
 *
 * <p>看起来只是个实验，实际会触发缓存的读防护（撞 id 扫描封禁）——
 * 这正是「间接副作用」的典型，判危险等级时最容易漏掉这一类。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CachePenetrationTool implements AgentTool {

    /**
     * 本工具超时，比默认长：要打上千次查询。
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /**
     * 查询次数上限。再大就会把读防护打到封禁，实验本身也失真。
     */
    private static final int MAX_COUNT = 5_000;

    /**
     * 默认查询次数。
     */
    private static final int DEFAULT_COUNT = 2_000;

    /**
     * cacheLabService，业务服务层。
     */
    private final CacheLabService cacheLabService;

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
        return "cache.penetration_lab";
    }

    /**
     * 获取工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String description() {
        return "跑一次缓存穿透实验，对比空值标记与布隆过滤器的耗时与拦截效果，需要用户确认后执行。"
                + "当用户问缓存穿透有多严重、两种防护差多少时调用；"
                + "只是想看命中率用 cache.stats。";
    }

    /**
     * 获取入参的 JSON Schema。
     *
     * @return JSON Schema 字符串
     */
    @Override
    public String parametersSchema() {
        return """
                {"type": "object", "properties": {"count": {"type": "integer",
                "description": "查询次数，默认 2000，最多 5000"}, "guarded": {"type": "boolean",
                "description": "是否开启防护，默认 true"}}, "required": []}""";
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
     * 跑一次穿透实验。
     *
     * @param arguments 入参
     * @return 执行结果
     */
    @Override
    public ToolResult invoke(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        int count = ToolArguments.intIn(arguments, "count", DEFAULT_COUNT, 1, Math.min(MAX_COUNT, Constants.MAX_BATCH_SIZE * 5));
        boolean guarded = ToolArguments.booleanIn(arguments, "guarded", true);
        try {
            Map<String, Object> result = cacheLabService.penetration(count, guarded);
            return ToolResult.ok(toolJson.writeTruncated(result, maxResultChars()), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("agent tool cache.penetration_lab failed count={} guarded={}", count, guarded, e);
            return ToolResult.fail("穿透实验执行失败：" + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

}
