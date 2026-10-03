package com.dong.agent.support.tool;

import com.dong.agent.enums.ToolRisk;
import com.dong.classic.service.LockLabService;
import com.dong.common.constant.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 锁并发对比工具。有副作用：会真的跑并发自增并写入实验结果表。
 *
 * <p>默认参数比 HTTP 接口小得多（8 线程 × 5 次）：加锁版实测 16×20 要跑十几秒，
 * 放在工具里会把整轮运行拖到超时。想看更大规模的对照，显式把参数调大。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LockLabTool implements AgentTool {

    /**
     * 本工具超时。加锁场景会串行等锁，默认 15 秒不够。
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    /**
     * 默认线程数。
     */
    private static final int DEFAULT_THREADS = 8;

    /**
     * 默认每线程循环次数。
     */
    private static final int DEFAULT_LOOPS = 5;

    /**
     * lockLabService，业务服务层。
     */
    private final LockLabService lockLabService;

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
        return "classic.lock_lab";
    }

    /**
     * 获取工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String description() {
        return "跑一次并发自增对比，观察加锁与不加锁的丢失更新数量，需要用户确认后执行。"
                + "当用户问分布式锁到底解决了什么问题时调用；"
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
                {"type": "object", "properties": {"threads": {"type": "integer",
                "description": "并发线程数，默认 8，最多 200"}, "loops": {"type": "integer",
                "description": "每线程循环次数，默认 5，最多 500"}}, "required": []}""";
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
     * 跑一次锁对比。
     *
     * @param arguments 入参
     * @return 执行结果
     */
    @Override
    public ToolResult invoke(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        int threads = ToolArguments.intIn(arguments, "threads", DEFAULT_THREADS, 1, Constants.MAX_THREADS);
        int loops = ToolArguments.intIn(arguments, "loops", DEFAULT_LOOPS, 1, Constants.MAX_LOOPS);
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("threads", threads);
            payload.put("loops", loops);
            payload.put("withLock", lockLabService.withLock(threads, loops));
            payload.put("withoutLock", lockLabService.withoutLock(threads, loops));
            return ToolResult.ok(toolJson.writeTruncated(payload, maxResultChars()), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("agent tool classic.lock_lab failed threads={} loops={}", threads, loops, e);
            return ToolResult.fail("锁对比执行失败：" + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

}
