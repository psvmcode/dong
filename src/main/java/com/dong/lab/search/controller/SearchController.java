package com.dong.lab.search.controller;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import com.dong.lab.common.result.Result;
import com.dong.lab.search.dto.ProductSearchRequest;
import com.dong.lab.search.dto.ProductSearchResponse;
import com.dong.lab.search.service.SearchService;
import com.dong.lab.search.service.SearchSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "搜索")
public class SearchController {

    private final ObjectProvider<SearchService> searchServiceProvider;

    private final ObjectProvider<SearchSyncService> searchSyncServiceProvider;

    /**
     * 全文检索，支持关键字、分类过滤、价格区间、高亮与分面聚合。
     */
    @GetMapping
    @Operation(summary = "全文检索，支持过滤、高亮与分面聚合")
    public Result<ProductSearchResponse> search(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String category,
                                                @RequestParam(required = false) Double minPrice,
                                                @RequestParam(required = false) Double maxPrice,
                                                @RequestParam(defaultValue = "1") int pageNum,
                                                @RequestParam(defaultValue = "20") int pageSize) {
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
     * 从 MySQL 全量重建索引。
     * 注意 bulk 接口即使单条失败也返回 200，必须检查响应里的 errors 标志，
     * 否则日期格式不匹配这类问题会静默吞掉所有写入。
     */
    @PostMapping("/sync")
    @Operation(summary = "从 MySQL 全量重建 Elasticsearch 索引")
    public Result<Integer> sync() {
        return Result.success(requireSyncService().syncAll());
    }

    /**
     * 查询索引中的文档总数。
     */
    @GetMapping("/count")
    @Operation(summary = "查询索引中的文档总数")
    public Result<Long> count() {
        return Result.success(requireSearchService().count());
    }

    private SearchService requireSearchService() {
        SearchService service = searchServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BusinessException(Constants.CODE_MIDDLEWARE_DISABLED,
                    "set lab.elasticsearch.enabled=true first");
        }
        return service;
    }

    private SearchSyncService requireSyncService() {
        SearchSyncService service = searchSyncServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BusinessException(Constants.CODE_MIDDLEWARE_DISABLED,
                    "set lab.elasticsearch.enabled=true first");
        }
        return service;
    }

}
