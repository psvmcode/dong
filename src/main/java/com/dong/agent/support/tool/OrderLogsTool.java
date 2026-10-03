package com.dong.agent.support.tool;

import com.dong.agent.enums.ToolRisk;
import com.dong.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 订单流转日志工具。被状态机拒绝的流转也记在里面，
 * 这正是「能不能推进」与「为什么推不动」的凭据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderLogsTool implements AgentTool {

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
        return "order.logs";
    }

    /**
     * 获取工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String description() {
        return "按订单号查询状态流转历史，成功与被拒绝的记录都在，含拒绝原因。"
                + "当用户问某笔订单经历过什么、为什么推进不了时调用；"
                + "要看订单列表用 order.recent。";
    }

    /**
     * 获取入参的 JSON Schema。
     *
     * @return JSON Schema 字符串
     */
    @Override
    public String parametersSchema() {
        return """
                {"type": "object", "properties": {"orderNo": {"type": "string",
                "description": "订单号。不知道订单号时先用 order.recent 查到它"}}, "required": ["orderNo"]}""";
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
     * 查询流转日志。
     *
     * @param arguments 入参，必填 orderNo
     * @return 执行结果
     */
    @Override
    public ToolResult invoke(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        String orderNo = ToolArguments.stringIn(arguments, "orderNo", "", 64);
        if (orderNo.isEmpty()) {
            return ToolResult.fail("缺少 orderNo，不知道订单号就先用 order.recent 查", System.currentTimeMillis() - start);
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("orderNo", orderNo);
            payload.put("logs", orderService.logs(orderNo));
            return ToolResult.ok(toolJson.writeTruncated(payload, maxResultChars()), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("agent tool order.logs failed orderNo={}", orderNo, e);
            return ToolResult.fail("查询流转日志失败：" + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

}
