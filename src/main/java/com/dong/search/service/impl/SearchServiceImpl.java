package com.dong.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.DistanceUnit;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationRange;
import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket;
import co.elastic.clients.elasticsearch._types.aggregations.RangeBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.NumberRangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.CompletionSuggestOption;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.Suggestion;
import com.dong.cache.enums.ProductStatus;
import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.search.config.IndexNameResolver;
import com.dong.search.dto.DeepSearchResponse;
import com.dong.search.dto.NearbySearchResponse;
import com.dong.search.dto.ProductSearchRequest;
import com.dong.search.dto.ProductSearchResponse;
import com.dong.search.dto.SearchAggregateResponse;
import com.dong.search.entity.ProductDocument;
import com.dong.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 商品检索实现。所有读写都打在别名上，真实索引由索引治理服务在后面换，
 * 这里完全不用关心数据是落在 v1 还是 v2。
 *
 * <p>几个贯穿全局的约定：
 * <ul>
 *   <li>写操作一律以 id 为文档 id 覆盖写，天然幂等，重复同步不会出问题。</li>
 *   <li>bulk 接口即使单条失败也返回 200，所以每个 bulk 调用都必须检查 errors 标志，
 *       否则日期格式不匹配这类问题会静默吞掉整批写入。</li>
 *   <li>过滤条件（分类、价格、是否下架）一律放 filter 而不是 must：
 *       filter 不参与打分，还自带缓存，同样条件下第二次查询更快。</li>
 *   <li>关键字走 must：它要参与 _score，用户搜的词越匹配排得越靠前。</li>
 * </ul>
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "dong.elasticsearch", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    /**
     * ES 默认的结果窗口上限。from + size 超过它 ES 直接报错，
     * 这是 ES 在保护自己：深分页用 from+size 的代价是线性增长的。
     */
    private static final int MAX_RESULT_WINDOW = 10_000;

    /**
     * 补全建议的 suggester 名字，取结果时用它当 key。
     */
    private static final String SUGGESTER_NAME = "product-suggest";

    /**
     * elasticsearchClient。
     */
    private final ElasticsearchClient elasticsearchClient;

    /**
     * indexNameResolver。
     */
    private final IndexNameResolver indexNameResolver;

    /**
     * index。
     */
    @Override
    public void index(ProductDocument document) {
        try {
            elasticsearchClient.index(builder -> builder
                    .index(indexName())
                    .id(document.getId())
                    .document(document));
            log.info("document indexed id={}", document.getId());
        } catch (Exception ex) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "index failed", ex);
        }
    }

    /**
     * bulkIndex。
     */
    @Override
    public void bulkIndex(Iterable<ProductDocument> documents) {
        List<ProductDocument> list = new ArrayList<>();
        documents.forEach(list::add);
        if (list.isEmpty()) {
            return;
        }
        try {
            List<BulkOperation> operations = list.stream()
                    .map(document -> new BulkOperation.Builder()
                            .index(index -> index
                                    .index(indexName())
                                    .id(document.getId())
                                    .document(document))
                            .build())
                    .toList();
            var response = elasticsearchClient.bulk(builder -> builder.index(indexName()).operations(operations));
            checkBulkResponse(response);
            log.info("bulk indexed {} documents", list.size());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "bulk index failed", ex);
        }
    }

    /**
     * deleteById。
     */
    @Override
    public void deleteById(String id) {
        try {
            elasticsearchClient.delete(builder -> builder.index(indexName()).id(id));
        } catch (Exception ex) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "delete failed", ex);
        }
    }

    /**
     * listAll。
     */
    @Override
    public Map<String, ProductDocument> listAll(int limit) {
        try {
            SearchResponse<ProductDocument> response = elasticsearchClient.search(builder -> builder
                    .index(indexName())
                    .size(limit)
                    .query(query -> query.matchAll(matchAll -> matchAll)), ProductDocument.class);
            Map<String, ProductDocument> documents = new LinkedHashMap<>();
            for (Hit<ProductDocument> hit : response.hits().hits()) {
                if (hit.source() != null) {
                    documents.put(hit.id(), hit.source());
                }
            }
            return documents;
        } catch (Exception ex) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "list all failed", ex);
        }
    }

    /**
     * bulkDelete。
     */
    @Override
    public void bulkDelete(Iterable<String> ids) {
        List<String> list = new ArrayList<>();
        ids.forEach(list::add);
        if (list.isEmpty()) {
            return;
        }
        try {
            List<BulkOperation> operations = list.stream()
                    .map(id -> new BulkOperation.Builder()
                            .delete(delete -> delete.index(indexName()).id(id))
                            .build())
                    .toList();
            var response = elasticsearchClient.bulk(builder -> builder.index(indexName()).operations(operations));
            checkBulkResponse(response);
            log.info("bulk deleted {} documents", list.size());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "bulk delete failed", ex);
        }
    }

    /**
     * deleteExcept。
     */
    @Override
    public long deleteExcept(Iterable<String> keepIds) {
        List<String> ids = new ArrayList<>();
        keepIds.forEach(ids::add);
        try {
            var response = elasticsearchClient.deleteByQuery(request -> request
                    .index(indexName())
                    .refresh(true)
                    .query(query -> query.bool(bool -> bool.mustNot(mustNot -> mustNot
                            .ids(idsQuery -> idsQuery.values(ids))))));
            long deleted = response.deleted() == null ? 0L : response.deleted();
            if (deleted > 0) {
                log.info("deleted {} documents outside the keep list", deleted);
            }
            return deleted;
        } catch (Exception ex) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "delete except failed", ex);
        }
    }

    /**
     * refresh。
     */
    @Override
    public void refresh() {
        try {
            elasticsearchClient.indices().refresh(request -> request.index(indexName()));
        } catch (Exception ex) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "refresh failed", ex);
        }
    }

    /**
     * count。
     */
    @Override
    public long count() {
        try {
            CountRequest request = CountRequest.of(builder -> builder.index(indexName()));
            return elasticsearchClient.count(request).count();
        } catch (Exception ex) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "count failed", ex);
        }
    }

    /**
     * 检索。关键字走 must 参与打分，过滤条件走 filter 不参与打分，
     * 两者混在 bool 里一次性下发给 ES。
     */
    @Override
    public ProductSearchResponse search(ProductSearchRequest request) {
        int from = Math.max(0, request.getPageNum() - 1) * request.getPageSize();
        if (from + request.getPageSize() > MAX_RESULT_WINDOW) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID,
                    "from+size exceeds max result window " + MAX_RESULT_WINDOW + ", use the deep paging api");
        }
        try {
            SearchRequest searchRequest = SearchRequest.of(builder -> builder
                    .index(indexName())
                    .from(from)
                    .size(request.getPageSize())
                    .query(filteredQuery(request))
                    .sort(sortOptions(request.getSort()))
                    .highlight(Highlight.of(highlight -> highlight
                            .fields("name", HighlightField.of(field -> field))
                            .fields("description", HighlightField.of(field -> field))))
                    .aggregations("categoryFacets", aggregation -> aggregation
                            .terms(terms -> terms.field("category"))));
            SearchResponse<ProductDocument> response =
                    elasticsearchClient.search(searchRequest, ProductDocument.class);
            ProductSearchResponse result = new ProductSearchResponse();
            result.setTotal(response.hits().total() == null ? 0L : response.hits().total().value());
            result.setPageNum(request.getPageNum());
            result.setPageSize(request.getPageSize());
            result.setList(toHits(response.hits().hits()));
            result.setCategoryFacets(toFacets(response.aggregations()));
            return result;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "search failed", ex);
        }
    }

    /**
     * 深分页。排序键必须唯一，所以价格或时间之后都补一个 id，
     * 否则同价的两条商品谁先谁后不确定，游标会漂，翻页出现重复或漏数据。
     */
    @Override
    public DeepSearchResponse searchAfter(String sort, String after, int size) {
        try {
            SearchRequest.Builder builder = new SearchRequest.Builder();
            builder.index(indexName())
                    .size(size)
                    .query(Query.of(query -> query.matchAll(matchAll -> matchAll)))
                    .sort(deepSortOptions(sort));
            List<FieldValue> cursor = parseCursor(sort, after);
            if (!cursor.isEmpty()) {
                builder.searchAfter(cursor);
            }
            SearchResponse<ProductDocument> response =
                    elasticsearchClient.search(builder.build(), ProductDocument.class);
            List<Hit<ProductDocument>> hits = response.hits().hits();

            DeepSearchResponse result = new DeepSearchResponse();
            result.setList(toHits(hits));
            // 取满一页才可能有下一页，取到的比请求少说明到头了
            result.setHasMore(hits.size() == size && !hits.isEmpty());
            result.setNextAfter(hits.isEmpty() ? null : toCursor(hits.get(hits.size() - 1).sort()));
            return result;
        } catch (Exception ex) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "deep search failed", ex);
        }
    }

    /**
     * 聚合。size 设成 0：只要聚合结果，不取文档，省掉一轮文档抓取。
     */
    @Override
    public SearchAggregateResponse aggregate(ProductSearchRequest request) {
        try {
            SearchRequest searchRequest = SearchRequest.of(builder -> builder
                    .index(indexName())
                    .size(0)
                    .query(filteredQuery(request))
                    .aggregations("categoryFacets", aggregation -> aggregation
                            .terms(terms -> terms.field("category").size(20))
                            .aggregations("priceStats", sub -> sub.stats(stats -> stats.field("price"))))
                    .aggregations("priceStats", aggregation -> aggregation.stats(stats -> stats.field("price")))
                    .aggregations("priceRanges", aggregation -> aggregation.range(range -> range
                            .field("price")
                            .ranges(List.of(
                                    AggregationRange.of(bucket -> bucket.to(100.0)),
                                    AggregationRange.of(bucket -> bucket.from(100.0).to(500.0)),
                                    AggregationRange.of(bucket -> bucket.from(500.0))))))
                    .aggregations("monthly", aggregation -> aggregation.dateHistogram(histogram -> histogram
                            .field("createTime")
                            .calendarInterval(CalendarInterval.Month))));
            SearchResponse<ProductDocument> response =
                    elasticsearchClient.search(searchRequest, ProductDocument.class);
            Map<String, Aggregate> aggregations = response.aggregations() == null
                    ? Map.of() : response.aggregations();

            SearchAggregateResponse result = new SearchAggregateResponse();
            result.setTotal(response.hits().total() == null ? 0L : response.hits().total().value());
            result.setCategoryFacets(new LinkedHashMap<>());
            result.setPriceStatsByCategory(new LinkedHashMap<>());

            Aggregate facets = aggregations.get("categoryFacets");
            if (facets != null && facets.isSterms()) {
                for (StringTermsBucket bucket : facets.sterms().buckets().array()) {
                    String category = bucket.key().stringValue();
                    result.getCategoryFacets().put(category, bucket.docCount());
                    Aggregate sub = bucket.aggregations() == null
                            ? null : bucket.aggregations().get("priceStats");
                    if (sub != null && sub.isStats()) {
                        result.getPriceStatsByCategory().put(category, toStats(sub));
                    }
                }
            }
            if (aggregations.get("priceStats") != null && aggregations.get("priceStats").isStats()) {
                result.setPriceStats(toStats(aggregations.get("priceStats")));
            }
            result.setPriceRanges(toRangeBuckets(aggregations.get("priceRanges")));
            result.setMonthlyBuckets(toDateBuckets(aggregations.get("monthly")));
            return result;
        } catch (Exception ex) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "aggregate failed", ex);
        }
    }

    /**
     * 前缀补全。
     */
    @Override
    public List<String> suggest(String prefix, int size) {
        try {
            SearchResponse<Void> response = elasticsearchClient.search(builder -> builder
                    .index(indexName())
                    .size(0)
                    .suggest(suggest -> suggest.suggesters(SUGGESTER_NAME, suggester -> suggester
                            .prefix(prefix)
                            .completion(completion -> completion
                                    .field("suggest")
                                    .size(size)
                                    .skipDuplicates(true)))), Void.class);
            if (response.suggest() == null) {
                return List.of();
            }
            List<Suggestion<Void>> suggestions = response.suggest().get(SUGGESTER_NAME);
            if (suggestions == null) {
                return List.of();
            }
            return suggestions.stream()
                    .filter(Suggestion::isCompletion)
                    .flatMap(suggestion -> suggestion.completion().options().stream())
                    .map(CompletionSuggestOption::text)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception ex) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "suggest failed", ex);
        }
    }

    /**
     * 附近商品。过滤与排序都用同一个坐标：过滤决定「哪些在圈里」，
     * 排序决定「谁更近」；只过滤不排序，返回顺序就是随机的。
     */
    @Override
    public NearbySearchResponse nearby(double latitude, double longitude, double radiusKm, int size) {
        try {
            SearchResponse<ProductDocument> response = elasticsearchClient.search(builder -> builder
                    .index(indexName())
                    .size(size)
                    .query(query -> query.bool(bool -> bool
                            .filter(filter -> filter.geoDistance(geo -> geo
                                    .field("location")
                                    .distance(radiusKm + "km")
                                    .location(location -> location.latlon(latlon -> latlon
                                            .lat(latitude)
                                            .lon(longitude)))))))
                    .sort(sort -> sort.geoDistance(geo -> geo
                            .field("location")
                            .location(location -> location.latlon(latlon -> latlon
                                    .lat(latitude)
                                    .lon(longitude)))
                            // 不指定 unit 时 ES 按米返回，直接当成公里用会差一千倍，
                            // 而且数值看起来「像公里」，是最难发现的一类 bug
                            .unit(DistanceUnit.Kilometers)
                            .order(SortOrder.Asc))), ProductDocument.class);

            NearbySearchResponse result = new NearbySearchResponse();
            result.setTotal(response.hits().total() == null ? 0L : response.hits().total().value());
            List<NearbySearchResponse.NearbyHit> list = new ArrayList<>();
            for (Hit<ProductDocument> hit : response.hits().hits()) {
                if (hit.source() == null) {
                    continue;
                }
                NearbySearchResponse.NearbyHit item = new NearbySearchResponse.NearbyHit();
                item.setId(hit.id());
                item.setName(hit.source().getName());
                item.setCategory(hit.source().getCategory());
                item.setPrice(hit.source().getPrice());
                item.setDistanceKm(toDistance(hit.sort()));
                list.add(item);
            }
            result.setList(list);
            return result;
        } catch (Exception ex) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "nearby search failed", ex);
        }
    }

    /**
     * 拼检索条件。关键字进 must 参与打分，其余进 filter 只做筛选。
     */
    private Query filteredQuery(ProductSearchRequest request) {
        BoolQuery.Builder bool = new BoolQuery.Builder();
        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            // name 权重 3 倍于 description：标题命中比正文命中更可信，
            // 而 fuzzy 容忍用户打错字，代价是可能召回一些不相关的长尾词
            bool.must(MultiMatchQuery.of(match -> match
                    .fields("name^3", "description")
                    .query(request.getKeyword())
                    .fuzziness("AUTO"))._toQuery());
        }
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            bool.filter(TermQuery.of(term -> term.field("category").value(request.getCategory()))._toQuery());
        }
        if (request.getMinPrice() != null || request.getMaxPrice() != null) {
            var numberRange = new NumberRangeQuery.Builder()
                    .field("price")
                    .gte(request.getMinPrice())
                    .lte(request.getMaxPrice());
            bool.filter(new RangeQuery.Builder().number(numberRange.build()).build()._toQuery());
        }
        if (!request.isIncludeOffShelf()) {
            bool.filter(TermQuery.of(term -> term
                    .field("status")
                    .value(ProductStatus.ON_SALE.name()))._toQuery());
        }
        return new Query.Builder().bool(bool.build()).build();
    }

    /**
     * 检索排序。按价格或时间排序时相当于放弃相关性，这是有代价的取舍，
     * 见 ProductSearchRequest 里 sort 字段的说明。
     */
    private List<SortOptions> sortOptions(String sort) {
        if (ProductSearchRequest.SORT_PRICE_ASC.equals(sort)) {
            return List.of(SortOptions.of(option -> option
                    .field(field -> field.field("price").order(SortOrder.Asc))));
        }
        if (ProductSearchRequest.SORT_PRICE_DESC.equals(sort)) {
            return List.of(SortOptions.of(option -> option
                    .field(field -> field.field("price").order(SortOrder.Desc))));
        }
        if (ProductSearchRequest.SORT_CREATED_DESC.equals(sort)) {
            return List.of(SortOptions.of(option -> option
                    .field(field -> field.field("createTime").order(SortOrder.Desc))));
        }
        return List.of(SortOptions.of(option -> option.score(score -> score.order(SortOrder.Desc))));
    }

    /**
     * 深分页排序。比普通排序多补一个 id 作为第二排序键，保证排序值唯一，
     * 否则同价商品的顺序不确定，游标会在两条之间来回跳。
     */
    private List<SortOptions> deepSortOptions(String sort) {
        List<SortOptions> options = new ArrayList<>(sortOptions(sort));
        options.add(SortOptions.of(option -> option
                .field(field -> field.field("id").order(SortOrder.Asc))));
        return options;
    }

    /**
     * 把游标字符串还原成排序值。第 0 位是价格或时间戳（数值），第 1 位是 id（字符串）。
     */
    private List<FieldValue> parseCursor(String sort, String after) {
        if (after == null || after.isBlank()) {
            return List.of();
        }
        String[] parts = after.split(",");
        List<FieldValue> values = new ArrayList<>();
        for (int index = 0; index < parts.length; index++) {
            if (index == 0) {
                values.add(FieldValue.of(Double.parseDouble(parts[index])));
            } else {
                values.add(FieldValue.of(parts[index]));
            }
        }
        return values;
    }

    /**
     * 把最后一条的排序值转成游标字符串，与 parseCursor 严格对应。
     */
    private String toCursor(List<FieldValue> sortValues) {
        if (sortValues == null || sortValues.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (FieldValue value : sortValues) {
            if (value.isDouble()) {
                parts.add(String.valueOf(value.doubleValue()));
            } else if (value.isLong()) {
                parts.add(String.valueOf(value.longValue()));
            } else {
                parts.add(value.stringValue());
            }
        }
        return String.join(",", parts);
    }

    /**
     * 检查 bulk 响应里的失败项。ES 的 bulk 接口即使单条失败也返回 200，
     * 只有 errors 标志为 true 才说明真有失败，此时必须把原因捞出来，
     * 否则日期格式不匹配这类问题会静默吞掉整批写入。
     *
     * @param response bulk 响应
     */
    private void checkBulkResponse(BulkResponse response) {
        if (!Boolean.TRUE.equals(response.errors())) {
            return;
        }
        String reason = response.items().stream()
                .filter(item -> item.error() != null)
                .map(item -> item.id() + ": " + item.error().reason())
                .limit(3)
                .reduce((first, second) -> first + "; " + second)
                .orElse("unknown");
        log.error("bulk request reported errors: {}", reason);
        throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "bulk request failed: " + reason);
    }

    /**
     * toHits。
     */
    private List<ProductSearchResponse.Hit> toHits(List<Hit<ProductDocument>> hits) {
        List<ProductSearchResponse.Hit> results = new ArrayList<>(hits.size());
        for (Hit<ProductDocument> hit : hits) {
            ProductDocument source = hit.source();
            if (source == null) {
                continue;
            }
            Map<String, List<String>> highlight = hit.highlight() == null ? Map.of() : hit.highlight();
            ProductSearchResponse.Hit item = new ProductSearchResponse.Hit();
            item.setId(hit.id());
            item.setName(source.getName());
            item.setCategory(source.getCategory());
            item.setPrice(source.getPrice());
            item.setStock(source.getStock());
            item.setHighlight(highlight.getOrDefault("name", List.of()));
            item.setDescriptionHighlight(highlight.getOrDefault("description", List.of()));
            results.add(item);
        }
        return results;
    }

    /**
     * toFacets。
     */
    private Map<String, Long> toFacets(Map<String, Aggregate> aggregations) {
        if (aggregations == null || aggregations.isEmpty()) {
            return Map.of();
        }
        Aggregate facet = aggregations.get("categoryFacets");
        if (facet == null || !facet.isSterms()) {
            return Map.of();
        }
        Map<String, Long> result = new LinkedHashMap<>();
        for (StringTermsBucket bucket : facet.sterms().buckets().array()) {
            result.put(bucket.key().stringValue(), bucket.docCount());
        }
        return result;
    }

    /**
     * 把 stats 聚合转成响应对象。ES 返回的都是字符串形式的浮点数，这里统一转成 Double。
     */
    private SearchAggregateResponse.Stats toStats(Aggregate aggregate) {
        var stats = aggregate.stats();
        SearchAggregateResponse.Stats result = new SearchAggregateResponse.Stats();
        result.setCount(stats.count());
        result.setMin(stats.min());
        result.setMax(stats.max());
        result.setAvg(stats.avg());
        result.setSum(stats.sum());
        return result;
    }

    /**
     * 区间聚合的桶。key 形如 *-100.0、100.0-500.0，是 ES 按区间边界拼出来的。
     */
    private List<SearchAggregateResponse.Bucket> toRangeBuckets(Aggregate aggregate) {
        if (aggregate == null || !aggregate.isRange()) {
            return List.of();
        }
        List<SearchAggregateResponse.Bucket> results = new ArrayList<>();
        for (RangeBucket bucket : aggregate.range().buckets().array()) {
            SearchAggregateResponse.Bucket item = new SearchAggregateResponse.Bucket();
            item.setKey(bucket.key());
            item.setDocCount(bucket.docCount());
            results.add(item);
        }
        return results;
    }

    /**
     * 时间直方图的桶。keyAsString 是 ES 格式化好的日期，比裸时间戳可读。
     */
    private List<SearchAggregateResponse.Bucket> toDateBuckets(Aggregate aggregate) {
        if (aggregate == null || !aggregate.isDateHistogram()) {
            return List.of();
        }
        List<SearchAggregateResponse.Bucket> results = new ArrayList<>();
        for (DateHistogramBucket bucket : aggregate.dateHistogram().buckets().array()) {
            SearchAggregateResponse.Bucket item = new SearchAggregateResponse.Bucket();
            item.setKey(bucket.keyAsString());
            item.setDocCount(bucket.docCount());
            results.add(item);
        }
        return results;
    }

    /**
     * 取地理距离排序值。它不在文档里，是 ES 按 _geo_distance 现算出来的，
     * 放在 sort 数组的第 0 位。
     */
    private Double toDistance(List<FieldValue> sortValues) {
        if (sortValues == null || sortValues.isEmpty()) {
            return null;
        }
        FieldValue value = sortValues.get(0);
        if (value.isDouble()) {
            return value.doubleValue();
        }
        try {
            return Double.parseDouble(value.stringValue());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * indexName。
     */
    private String indexName() {
        return indexNameResolver.resolve(IndexNameResolver.PRODUCT_INDEX);
    }

}
