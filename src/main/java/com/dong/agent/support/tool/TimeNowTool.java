package com.dong.agent.support.tool;

import com.dong.agent.enums.ToolRisk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 当前时间工具。给模型一个权威时间源——模型自身不知道「现在」，
 * 不给它这个工具，遇到时间相关的问题就只能编。
 *
 * <p>只读，无副作用。时区用 ZoneId.of 解析并捕获异常：
 * 模型可能给出任意字符串，解析失败按失败结果回传，绝不向上抛。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeNowTool implements AgentTool {

    /**
     * 时间格式。
     */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
        return "time.now";
    }

    /**
     * 获取工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String description() {
        return "获取当前时间，可指定时区。"
                + "当用户问及现在几点、今天几号、某时区当前时间时调用；"
                + "问的是历史时间点或时间段时不要用这个工具。";
    }

    /**
     * 获取入参的 JSON Schema。
     *
     * @return JSON Schema 字符串
     */
    @Override
    public String parametersSchema() {
        return """
                {"type": "object", "properties": {"zone": {"type": "string",
                "description": "时区标识，如 Asia/Shanghai、UTC，不传默认 Asia/Shanghai"}}, "required": []}""";
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
     * 查询当前时间。
     *
     * @param arguments 入参，可选 zone
     * @return 执行结果
     */
    @Override
    public ToolResult invoke(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        String zone = ToolArguments.stringIn(arguments, "zone", "Asia/Shanghai", 64);
        try {
            ZoneId zoneId = ZoneId.of(zone);
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("zone", zoneId.getId());
            payload.put("datetime", FORMATTER.format(LocalDateTime.now(zoneId)));
            payload.put("epochMillis", String.valueOf(System.currentTimeMillis()));
            return ToolResult.ok(toolJson.write(payload), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("agent tool time.now invalid zone={}", zone);
            return ToolResult.fail("unknown time zone " + zone + ", use a valid zone id like Asia/Shanghai or UTC",
                    System.currentTimeMillis() - start);
        }
    }

}
