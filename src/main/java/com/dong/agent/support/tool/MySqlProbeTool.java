package com.dong.agent.support.tool;

import com.dong.agent.enums.ToolRisk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读数据库探查工具。
 *
 * <p>用白名单表而不是「校验 SQL 是否以 select 开头」：
 * 黑名单是在猜攻击者会怎么绕，而 SQL 的绕过方式（注释、编码、堆叠）猜不完。
 * 白名单的代价是只能查登记过的表，对这个工具来说完全够用。
 *
 * <p>默认关闭。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dong.agent.tools", name = "mysql-probe-enabled", havingValue = "true")
public class MySqlProbeTool implements AgentTool {

    /**
     * 单条返回上限。
     */
    private static final int MAX_ROWS = 50;

    /**
     * jdbcTemplate，只读查询用。
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * toolJson，工具结果的序列化与截断。
     */
    private final ToolJson toolJson;

    /**
     * 允许查询的表，逗号分隔。
     */
    @Value("${dong.agent.tools.mysql-probe-tables:}")
    private String allowedTables;

    /**
     * 获取工具名。
     *
     * @return 工具名
     */
    @Override
    public String name() {
        return "mysql.probe";
    }

    /**
     * 获取工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String description() {
        return "按表名只读查询最近的若干条记录，只能查登记过的表，不支持自定义条件。"
                + "需要了解项目里真实数据长什么样时调用；"
                + "需要改数据、或查未登记的表时不要用这个工具。";
    }

    /**
     * 获取入参的 JSON Schema。
     *
     * @return JSON Schema 字符串
     */
    @Override
    public String parametersSchema() {
        return """
                {"type": "object", "properties": {"table": {"type": "string",
                "description": "表名，必须是允许查询的表之一"}, "limit": {"type": "integer",
                "description": "返回条数，默认 10，最多 50"}}, "required": ["table"]}""";
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
     * 查询表数据。
     *
     * @param arguments 入参，必填 table，可选 limit
     * @return 执行结果
     */
    @Override
    public ToolResult invoke(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        String table = ToolArguments.stringIn(arguments, "table", "", 64);
        List<String> tables = allowed();
        if (table.isEmpty()) {
            return ToolResult.fail("缺少 table，可查询的表有：" + tables, System.currentTimeMillis() - start);
        }
        if (!tables.contains(table)) {
            return ToolResult.fail("表 " + table + " 不允许查询，可查询的表有：" + tables, System.currentTimeMillis() - start);
        }
        int limit = ToolArguments.intIn(arguments, "limit", 10, 1, MAX_ROWS);
        try {
            // 表名来自白名单，不是拼接进来的用户输入，这里不存在注入面
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select * from " + table + " order by id desc limit " + limit);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("table", table);
            payload.put("rows", rows);
            return ToolResult.ok(toolJson.writeTruncated(payload, maxResultChars()), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("agent tool mysql.probe failed table={}", table, e);
            return ToolResult.fail("查询失败：" + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    /**
     * 解析允许查询的表名清单。
     *
     * @return 表名列表
     */
    private List<String> allowed() {
        if (allowedTables == null || allowedTables.isBlank()) {
            return List.of();
        }
        return Arrays.stream(allowedTables.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

}
