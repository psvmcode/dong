package com.dong.agent.support.tool;

import com.dong.agent.enums.ToolRisk;
import com.dong.redpacket.service.RedPacketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 红包剩余查询工具。剩余份数与剩余金额一起返回，
 * 只给一个的话模型会自己「推算」另一个。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedPacketRemainTool implements AgentTool {

    /**
     * redPacketService，业务服务层。
     */
    private final RedPacketService redPacketService;

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
        return "redpacket.remain";
    }

    /**
     * 获取工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String description() {
        return "按红包编号查询剩余份数与剩余金额（单位分）。"
                + "当用户问某个红包还剩多少时调用，必须知道红包编号；"
                + "想发红包或抢红包时不要用这个工具。";
    }

    /**
     * 获取入参的 JSON Schema。
     *
     * @return JSON Schema 字符串
     */
    @Override
    public String parametersSchema() {
        return """
                {"type": "object", "properties": {"packetNo": {"type": "string",
                "description": "红包编号，形如 RP20261002001。不知道编号时不要用这个工具"}},
                "required": ["packetNo"]}""";
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
     * 查询红包剩余。
     *
     * @param arguments 入参，必填 packetNo
     * @return 执行结果
     */
    @Override
    public ToolResult invoke(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        String packetNo = ToolArguments.stringIn(arguments, "packetNo", "", 64);
        if (packetNo.isEmpty()) {
            return ToolResult.fail("缺少 packetNo，请给出红包编号", System.currentTimeMillis() - start);
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("packetNo", packetNo);
            payload.put("remainCount", redPacketService.remainCount(packetNo));
            payload.put("remainAmount", redPacketService.remainAmount(packetNo));
            return ToolResult.ok(toolJson.write(payload), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("agent tool redpacket.remain failed packetNo={}", packetNo, e);
            return ToolResult.fail("查询红包失败：" + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

}
