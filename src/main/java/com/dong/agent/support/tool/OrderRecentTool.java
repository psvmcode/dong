package com.dong.agent.support.tool;

import com.dong.agent.enums.ToolRisk;
import com.dong.common.constant.Constants;
import com.dong.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 订单查询工具。订单字段较多，结果按字符上限截断，
 * 模型看不到全量时可以缩小 limit 再查一次。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderRecentTool implements AgentTool {

    /**
     * orderService，业务服务层。
     */
    private final OrderService orderService;

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
        return "order.recent";
    }

    /**
     * 获取工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String description() {
        return "查询最近创建的订单，返回订单号、状态、金额与创建时间。"
                + "当用户想看订单列表时调用；"
                + "要查某一笔订单经历了哪些状态流转用 order.logs，而不是这个工具。";
    }

    /**
     * 获取入参的 JSON Schema。
     *
     * @return JSON Schema 字符串
     */
    @Override
    public String parametersSchema() {
        return """
                {"type": "object", "properties": {"limit": {"type": "integer", "minimum": 1, "maximum": 20,
                "description": "返回条数，默认 10"}}, "required": []}""";
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
     * 查询最近订单。
     *
     * @param arguments 入参，可选 limit
     * @return 执行结果
     */
    @Override
    public ToolResult invoke(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        int limit = ToolArguments.intIn(arguments, "limit", 10, 1, Constants.MAX_QUERY_LIMIT);
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("limit", limit);
            payload.put("orders", orderService.recent(limit));
            return ToolResult.ok(toolJson.writeTruncated(payload, maxResultChars()), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("agent tool order.recent failed limit={}", limit, e);
            return ToolResult.fail("查询订单失败：" + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

}
