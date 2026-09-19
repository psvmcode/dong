package com.dong.search.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.validation.annotation.Validated;
import com.dong.common.exception.BusinessException;
import com.dong.common.result.Result;
import com.dong.search.dto.ConsistencyReport;
import com.dong.search.dto.DeepSearchResponse;
import com.dong.search.dto.NearbySearchResponse;
import com.dong.search.dto.ProductSearchRequest;
import com.dong.search.dto.ProductSearchResponse;
import com.dong.search.dto.RebuildResponse;
import com.dong.search.dto.SearchAggregateResponse;
import com.dong.search.service.SearchIndexService;
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

import java.util.List;

/**
 * 商品搜索。一个模块里摆了 ES 最常见的几类用法，
 * 每一类都对应一种真实业务诉求，也各有一条明确的边界：
 *
 * <ul>
 *   <li>检索（GET /）：关键字打分加条件过滤，是最高频的入口</li>
 *   <li>深分页（GET /deep）：导出、全量遍历这类要翻很深的场景，不能用 from+size</li>
 *   <li>聚合（GET /aggregate）：筛选面板、价格分布、增长趋势，要的是统计不是文档</li>
 *   <li>补全（GET /suggest）：搜索框下拉提示，要的是毫秒级响应</li>
 *   <li>地理（GET /nearby）：同城、附近门店，要的是距离过滤加距离排序</li>
 *   <li>索引治理（POST /rebuild）：改 mapping、换分词器只能重建，靠别名做到不停机</li>
 *   <li>一致性（GET /consistency、POST /sync*）：库是权威源，索引漂移要能自查自修</li>
 * </ul>
 *
 * <p>接口全部按 dong.elasticsearch.enabled 开关启停，
 * 没开时返回中间件未启用，而不是把 ES 错误直接抛给调用方。
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
     * searchIndexServiceProvider，缓存提供者。
     */
    private final ObjectProvider<SearchIndexService> searchIndexServiceProvider;

    /**
     * 全文检索。关键字走 must 参与打分，分类、价格、上下架状态走 filter 只做筛选，
     * 结果带 name 与 description 两个字段的高亮，外加分类分面。
     */
    @GetMapping
    @Operation(summary = "全文检索，支持过滤、排序、多字段高亮与分面聚合")
    public Result<ProductSearchResponse> search(@RequestParam(required = false) @Size(max = 256) String keyword, @RequestParam(required = false) @Size(max = 128) String category, @RequestParam(required = false) @PositiveOrZero Double minPrice, @RequestParam(required = false) @PositiveOrZero Double maxPrice, @RequestParam(defaultValue = ProductSearchRequest.SORT_RELEVANCE) @Pattern(regexp = ProductSearchRequest.SORT_PATTERN) String sort, @RequestParam(defaultValue = "false") boolean includeOffShelf, @RequestParam(defaultValue = "1") @Min(1) @Max(Constants.MAX_PAGE_NUM) int pageNum, @RequestParam(defaultValue = "20") @Min(1) @Max(Constants.MAX_PAGE_SIZE) int pageSize) {
        return Result.success(requireSearchService().search(toRequest(keyword, category, minPrice, maxPrice, sort, includeOffShelf, pageNum, pageSize)));
    }

    /**
     * 深分页。第一页不带 after，之后把上一页返回的 nextAfter 原样传回来。
     * 只能一页页往下翻，不支持跳页——这是 search_after 的固有代价。
     */
    @GetMapping("/deep")
    @Operation(summary = "深分页检索，用 search_after 翻页，不受 from+size 的一万条上限限制")
    public Result<DeepSearchResponse> deep(@RequestParam(defaultValue = ProductSearchRequest.SORT_PRICE_ASC) @Pattern(regexp = ProductSearchRequest.SORT_PATTERN) String sort, @RequestParam(required = false) @Size(max = 128) String after, @RequestParam(defaultValue = "20") @Min(1) @Max(Constants.MAX_PAGE_SIZE) int size) {
        return Result.success(requireSearchService().searchAfter(sort, after, size));
    }

    /**
     * 聚合统计。过滤条件与检索一致，统计的就是用户在当前筛选下能看到的那批数据。
     * 一次返回分面、价格统计、价格区间分布、按月趋势，以及每个分类各自的均价。
     */
    @GetMapping("/aggregate")
    @Operation(summary = "聚合统计：分类分面、价格统计、价格区间分布、按月趋势")
    public Result<SearchAggregateResponse> aggregate(@RequestParam(required = false) @Size(max = 256) String keyword, @RequestParam(required = false) @Size(max = 128) String category, @RequestParam(required = false) @PositiveOrZero Double minPrice, @RequestParam(required = false) @PositiveOrZero Double maxPrice, @RequestParam(defaultValue = "false") boolean includeOffShelf) {
        return Result.success(requireSearchService().aggregate(toRequest(keyword, category, minPrice, maxPrice, ProductSearchRequest.SORT_RELEVANCE, includeOffShelf, Constants.DEFAULT_PAGE_NUM, Constants.DEFAULT_PAGE_SIZE)));
    }

    /**
     * 前缀补全。只匹配 suggest 字段的前缀，不是全文检索：
     * 用户输入「智」能补出「智能手机」，但输入「手机」补不出来。
     */
    @GetMapping("/suggest")
    @Operation(summary = "搜索框前缀补全，走 completion suggester")
    public Result<List<String>> suggest(@RequestParam @NotBlank @Size(max = 128) String prefix, @RequestParam(defaultValue = "10") @Min(1) @Max(Constants.MAX_PAGE_SIZE) int size) {
        return Result.success(requireSearchService().suggest(prefix, size));
    }

    /**
     * 附近商品。按给定坐标做半径过滤并按距离由近到远排序。
     * 商品没填经纬度时不会被算进结果，这是预期行为而不是漏数据。
     */
    @GetMapping("/nearby")
    @Operation(summary = "按坐标检索附近商品，半径过滤加距离排序")
    public Result<NearbySearchResponse> nearby(@RequestParam @DecimalMin("-90") @DecimalMax("90") double lat, @RequestParam @DecimalMin("-180") @DecimalMax("180") double lon, @RequestParam(defaultValue = "10") @Positive @Max(20_000) double radiusKm, @RequestParam(defaultValue = "20") @Min(1) @Max(Constants.MAX_PAGE_SIZE) int size) {
        return Result.success(requireSearchService().nearby(lat, lon, radiusKm, size));
    }

    /**
     * 零停机重建索引。改了 mapping（比如加了字段类型、换了分词器）之后用它生效，
     * 全程别名不变，应用读写不受影响。
     */
    @PostMapping("/rebuild")
    @Operation(summary = "零停机重建索引：建新版本、搬数据、切别名、删旧索引")
    public Result<RebuildResponse> rebuild() {
        return Result.success(requireIndexService().rebuild());
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
    public Result<Void> syncOne(@PathVariable @Positive Long productId) {
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
     * 当前别名指向的真实索引。排查「数据到底写哪去了」时先看它。
     */
    @GetMapping("/current-index")
    @Operation(summary = "查看别名当前指向的真实索引")
    public Result<String> currentIndex() {
        return Result.success(requireIndexService().currentIndex());
    }

    /**
     * 组装检索请求。检索与聚合共用同一套过滤，统计口径才不会和列表对不上。
     */
    private ProductSearchRequest toRequest(String keyword, String category, Double minPrice, Double maxPrice, String sort, boolean includeOffShelf, int pageNum, int pageSize) {
        ProductSearchRequest request = new ProductSearchRequest();
        request.setKeyword(keyword);
        request.setCategory(category);
        request.setMinPrice(minPrice);
        request.setMaxPrice(maxPrice);
        request.setSort(sort);
        request.setIncludeOffShelf(includeOffShelf);
        request.setPageNum(pageNum);
        request.setPageSize(pageSize);
        return request;
    }

    /**
     * requireSearchService。
     */
    private SearchService requireSearchService() {
        SearchService service = searchServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BusinessException(Constants.CODE_MIDDLEWARE_DISABLED, "set dong.elasticsearch.enabled=true first");
        }
        return service;
    }

    /**
     * requireSyncService。
     */
    private SearchSyncService requireSyncService() {
        SearchSyncService service = searchSyncServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BusinessException(Constants.CODE_MIDDLEWARE_DISABLED, "set dong.elasticsearch.enabled=true first");
        }
        return service;
    }

    /**
     * requireIndexService。
     */
    private SearchIndexService requireIndexService() {
        SearchIndexService service = searchIndexServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BusinessException(Constants.CODE_MIDDLEWARE_DISABLED, "set dong.elasticsearch.enabled=true first");
        }
        return service;
    }

}
