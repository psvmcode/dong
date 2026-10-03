package com.dong.agent.support.tool;

import com.dong.agent.enums.ToolRisk;
import com.dong.search.dto.ProductSearchRequest;
import com.dong.search.dto.ProductSearchResponse;
import com.dong.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 项目知识库检索工具，底层是 Elasticsearch 加 IK 中文分词。
 *
 * <p>用 ObjectProvider 而不是直接注入：SearchServiceImpl 带 ES 开关，
 * 关掉时容器里没有这个 bean，硬注入会让整个应用起不来——
 * 一个可选模块不该决定主应用的生死，缺了就明确报不可用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchQueryTool implements AgentTool {

    /**
     * 返回条数上限。检索命中的文档字段很长，条数必须压住。
     */
    private static final int MAX_HITS = 10;

    /**
     * searchServiceProvider，ES 检索服务的可选提供者。
     */
    private final ObjectProvider<SearchService> searchServiceProvider;

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
        return "search.query";
    }

    /**
     * 获取工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String description() {
        return "用中文关键词检索项目里的商品资料，返回命中条数与高亮片段。"
                + "当用户问项目里有没有某个东西、想查商品资料时调用；"
                + "问的是订单、红包、秒杀这些应该用各自对应的工具，不要用检索。";
    }

    /**
     * 获取入参的 JSON Schema。
     *
     * @return JSON Schema 字符串
     */
    @Override
    public String parametersSchema() {
        return """
                {"type": "object", "properties": {"keyword": {"type": "string",
                "description": "检索关键词，支持中文"}, "limit": {"type": "integer",
                "description": "返回条数，默认 5，最多 10"}}, "required": ["keyword"]}""";
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
     * 执行检索。
     *
     * @param arguments 入参，必填 keyword
     * @return 执行结果
     */
    @Override
    public ToolResult invoke(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        SearchService service = searchServiceProvider.getIfAvailable();
        if (service == null) {
            return ToolResult.fail("Elasticsearch 未启用，检索不可用，请先在配置里打开 dong.elasticsearch.enabled",
                    System.currentTimeMillis() - start);
        }
        String keyword = ToolArguments.stringIn(arguments, "keyword", "", 128);
        if (keyword.isEmpty()) {
            return ToolResult.fail("缺少 keyword，请给出检索关键词", System.currentTimeMillis() - start);
        }
        int limit = ToolArguments.intIn(arguments, "limit", 5, 1, MAX_HITS);
        try {
            ProductSearchRequest request = new ProductSearchRequest();
            request.setKeyword(keyword);
            request.setPageNum(1);
            request.setPageSize(limit);
            ProductSearchResponse response = service.search(request);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("keyword", keyword);
            payload.put("total", response == null ? 0 : response.getTotal());
            payload.put("list", response == null ? java.util.List.of() : response.getList());
            return ToolResult.ok(toolJson.writeTruncated(payload, maxResultChars()), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("agent tool search.query failed keyword={}", keyword, e);
            return ToolResult.fail("检索失败：" + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

}
