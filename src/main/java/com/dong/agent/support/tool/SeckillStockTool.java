package com.dong.agent.support.tool;

import com.dong.agent.enums.ToolRisk;
import com.dong.common.constant.Constants;
import com.dong.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 秒杀库存查询工具。只查 Redis 里的剩余库存，不改任何状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillStockTool implements AgentTool {

    /**
     * seckillService，业务服务层。
     */
    private final SeckillService seckillService;

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
        return "seckill.stock";
    }

    /**
     * 获取工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String description() {
        return "按活动 id 查询秒杀活动的剩余库存。"
                + "当用户问某个秒杀还剩多少件时调用；"
                + "想看全部活动列表、或要下单时不要用这个工具。";
    }

    /**
     * 获取入参的 JSON Schema。
     *
     * @return JSON Schema 字符串
     */
    @Override
    public String parametersSchema() {
        return """
                {"type": "object", "properties": {"activityId": {"type": "integer",
                "description": "秒杀活动 id，正整数"}}, "required": ["activityId"]}""";
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
     * 查询剩余库存。
     *
     * @param arguments 入参，必填 activityId
     * @return 执行结果
     */
    @Override
    public ToolResult invoke(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        int activityId = ToolArguments.intIn(arguments, "activityId", 0, 1, Constants.MAX_BATCH_SIZE);
        if (activityId == 0) {
            return ToolResult.fail("缺少 activityId，请给出秒杀活动 id", System.currentTimeMillis() - start);
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("activityId", activityId);
            payload.put("stock", seckillService.stockOf((long) activityId));
            return ToolResult.ok(toolJson.write(payload), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("agent tool seckill.stock failed activityId={}", activityId, e);
            return ToolResult.fail("查询库存失败：" + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

}
