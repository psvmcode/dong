package com.dong.agent.support.tool;

import com.dong.agent.enums.ToolRisk;
import com.dong.classic.mapper.ClassicLabRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 历史实验结果查询工具。查的是已经跑过的实验记录，
 * 与「再跑一次」的实验类工具分开：看过往数据时不该产生新的实验流量。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClassicLabRecordTool implements AgentTool {

    /**
     * 结果条数上限。
     */
    private static final int MAX_RECORDS = 20;

    /**
     * labRecordMapper，MyBatis Mapper 数据访问层。
     */
    private final ClassicLabRecordMapper labRecordMapper;

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
        return "classic.lab_record";
    }

    /**
     * 获取工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String description() {
        return "查询历史上跑过的实验结果：kind 取 lock 查锁并发对比，取 limiter 查限流算法对比。"
                + "只是想看之前的结论时调用；"
                + "要现场跑一次对比，应该用 classic.lock_lab 或 classic.limiter_compare。";
    }

    /**
     * 获取入参的 JSON Schema。
     *
     * @return JSON Schema 字符串
     */
    @Override
    public String parametersSchema() {
        return """
                {"type": "object", "properties": {"kind": {"type": "string", "enum": ["lock", "limiter"],
                "description": "实验类型，默认 lock"}, "mode": {"type": "string",
                "description": "锁实验的模式：with-lock 或 no-lock，默认 with-lock"},
                "limit": {"type": "integer", "description": "返回条数，默认 5，最多 20"}}, "required": []}""";
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
     * 查询历史实验记录。
     *
     * @param arguments 入参
     * @return 执行结果
     */
    @Override
    public ToolResult invoke(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        String kind = ToolArguments.stringIn(arguments, "kind", "lock", 16);
        int limit = ToolArguments.intIn(arguments, "limit", 5, 1, MAX_RECORDS);
        try {
            Object records;
            Map<String, Object> payload = new LinkedHashMap<>();
            if ("limiter".equals(kind)) {
                String bizKey = ToolArguments.stringIn(arguments, "bizKey", "demo", 64);
                records = labRecordMapper.selectRateLimitResult(bizKey, limit);
                payload.put("bizKey", bizKey);
            } else {
                String mode = ToolArguments.stringIn(arguments, "mode", "with-lock", 16);
                records = labRecordMapper.selectLockLabResult(mode, limit);
                payload.put("mode", mode);
            }
            payload.put("kind", kind);
            payload.put("records", records);
            return ToolResult.ok(toolJson.writeTruncated(payload, maxResultChars()), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("agent tool classic.lab_record failed kind={}", kind, e);
            return ToolResult.fail("查询实验记录失败：" + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

}
