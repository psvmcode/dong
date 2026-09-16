package com.dong.search.controller;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.validation.annotation.Validated;
import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.common.result.Result;
import com.dong.search.dto.ConsistencyReport;
import com.dong.search.dto.ProductSearchRequest;
import com.dong.search.dto.ProductSearchResponse;
import com.dong.search.service.SearchService;
import com.dong.search.service.SearchSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品搜索。索引映射由启动时显式创建，category 为 keyword 以支持聚合，
 * name 和 description 用 ik_max_word 索引、ik_smart 查询。
 *
 * <p>不能依赖动态映射：ES 会把字符串默认推断成 text，
 * 而对 text 字段做 terms 聚合是非法的，分面统计会直接报错。
 */
@RestController
@Validated
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "搜索")

public class SearchController {

    /**
     * searchServiceProvider，缓存提供者。
     */
    private final ObjectProvider<SearchService> searchServiceProvider;

    /**
     * searchSyncServiceProvider，缓存提供者。
     */
    private final ObjectProvider<SearchSyncService> searchSyncServiceProvider;

    /**
     * 全文检索，支持关键字、分类过滤、价格区间、高亮与分面聚合。
     *
     * <p>三个过滤条件都是可选的，全不传等价于按分页遍历。可选参数上只能加 @Size，
     * 一旦加 @NotBlank，required=false 就形同虚设，方法体里的 null 分支也永远走不到。
     */
    @GetMapping
    @Operation(summary = "全文检索，支持过滤、高亮与分面聚合")
    public Result<ProductSearchResponse> search(@RequestParam(required = false)
                                                @Size(max = 256) String keyword,
                                                @RequestParam(required = false)
                                                @Size(max = 128) String category,
                                                @RequestParam(required = false)
                                                @PositiveOrZero Double minPrice,
                                                @RequestParam(required = false)
                                                @PositiveOrZero Double maxPrice,
                                                @RequestParam(defaultValue = "1")
                                                @Min(1) @Max(Constants.MAX_PAGE_NUM) int pageNum,
                                                @RequestParam(defaultValue = "20")
                                                @Min(1) @Max(Constants.MAX_PAGE_SIZE) int pageSize) {
        ProductSearchRequest request = new ProductSearchRequest();
        request.setKeyword(keyword);
        request.setCategory(category);
        request.setMinPrice(minPrice);
        request.setMaxPrice(maxPrice);
        request.setPageNum(pageNum);
        request.setPageSize(pageSize);
        return Result.success(requireSearchService().search(request));
    }

    /**
     * 从 MySQL 全量重建索引，顺带清理数据库里已经不存在的孤儿文档。
     * 注意 bulk 接口即使单条失败也返回 200，必须检查响应里的 errors 标志，
     * 否则日期格式不匹配这类问题会静默吞掉所有写入。
     */
    @PostMapping("/sync")
    @Operation(summary = "从 MySQL 全量重建 Elasticsearch 索引，并清理孤儿文档")
    public Result<Integer> sync() {
        return Result.success(requireSyncService().syncAll());
    }

    /**
     * 重同步单个商品。按 id 回查数据库：查得到就覆盖索引文档，查不到就把索引文档删掉。
     * 用于单条数据出问题时的定点修复，比全量重建轻得多。
     */
    @PostMapping("/sync/{productId}")
    @Operation(summary = "按 id 重同步单个商品，库里有则覆盖文档，没有则删除文档")
    public Result<Void> syncOne(@PathVariable
 @Positive Long productId) {
        requireSyncService().syncOne(productId);
        return Result.success();
    }

    /**
     * 一致性对账，只读不写。报告三类差异：库里有索引没有（漏同步）、
     * 两边都有但内容对不上（同步到一半，或者有人绕过应用直接改过索引）、
     * 索引有库里没有（孤儿文档，搜索时会返回已经不存在的商品）。
     */
    @GetMapping("/consistency")
    @Operation(summary = "ES 与 MySQL 一致性对账，只报告不修复")
    public Result<ConsistencyReport> consistency() {
        return Result.success(requireSyncService().checkConsistency());
    }

    /**
     * 按对账结果修复：补写缺失与内容过期的文档，删除孤儿文档。
     * 数据量超过上限会直接拒绝执行，因为截断之后没查到的那一侧会被当成孤儿文档误删。
     */
    @PostMapping("/consistency/repair")
    @Operation(summary = "按对账结果修复 ES，补写缺失与过期文档并删除孤儿文档")
    public Result<ConsistencyReport> repairConsistency() {
        return Result.success(requireSyncService().repairConsistency());
    }

    /**
     * 查询索引中的文档总数。
     */
    @GetMapping("/count")
    @Operation(summary = "查询索引中的文档总数")
    public Result<Long> count() {
        return Result.success(requireSearchService().count());
    }

    /**
     * requireSearchService。
     */
    private SearchService requireSearchService() {
        SearchService service = searchServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BusinessException(Constants.CODE_MIDDLEWARE_DISABLED,
                    "set dong.elasticsearch.enabled=true first");
        }
        return service;
    }

    /**
     * requireSyncService。
     */
    private SearchSyncService requireSyncService() {
        SearchSyncService service = searchSyncServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BusinessException(Constants.CODE_MIDDLEWARE_DISABLED,
                    "set dong.elasticsearch.enabled=true first");
        }
        return service;
    }

}
