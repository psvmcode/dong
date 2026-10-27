package com.dong.logging.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.DateRangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.PrefixQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.logging.config.LogIndexNaming;
import com.dong.logging.dto.LogContextResponse;
import com.dong.logging.dto.LogOverviewResponse;
import com.dong.logging.dto.LogSearchRequest;
import com.dong.logging.dto.LogSearchResponse;
import com.dong.logging.entity.LogDocument;
import com.dong.logging.service.LogSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 日志检索实现。
 *
 * <p>几个与商品检索不同的取舍：
 * <ul>
 *   <li><b>默认按时间倒序，不用相关性。</b>日志检索几乎都是「看刚刚发生了什么」，
 *       一条 ERROR 不会因为词频高就更值得看。</li>
 *   <li><b>过滤一律进 filter 不进 must。</b>时间范围、级别、logger 前缀都不参与打分，
 *       还能吃到 filter 缓存；只有关键字进 must 参与 _score。</li>
 *   <li><b>索引还没建时返回空而不是报错。</b>应用刚起来、一条日志都还没采集到的时候，
 *       检索页面应该显示「暂无数据」，而不是弹一个 ES 索引不存在的错误。</li>
 * </ul>
 *
 * <p>一万条结果窗口是 ES 的硬限制，翻更深只能收窄时间范围——
 * 日志这种按时间有序的数据，收窄范围永远比深翻页快。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"dong.logging.enabled", "dong.elasticsearch.enabled"}, havingValue = "true")
public class LogSearchServiceImpl implements LogSearchService {

    /**
     * ES 的结果窗口上限。
     */
    private static final int MAX_RESULT_WINDOW = 10_000;

    /**
     * 单个 terms 聚合返回的桶数。
     */
    private static final int MAX_TERMS = 20;

    /**
     * 时间直方图的目标桶数。用 auto_date_histogram 由 ES 自己挑间隔：
     * 时间范围从 15 分钟到 7 天都在同一个接口里，写死间隔必然顾此失彼。
     */
    private static final int TIMELINE_BUCKETS = 60;

    /**
     * 级别分布聚合名。
     */
    private static final String AGG_LEVELS = "byLevel";

    /**
     * 时间直方图聚合名。
     */
    private static final String AGG_TIMELINE = "timeline";

    /**
     * 直方图中按级别拆分的子聚合名。
     */
    private static final String AGG_TIMELINE_LEVELS = "levels";

    /**
     * Top logger 聚合名。
     */
    private static final String AGG_LOGGERS = "topLoggers";

    /**
     * 错误过滤聚合名。
     */
    private static final String AGG_ERRORS = "errors";

    /**
     * 错误类型子聚合名。
     */
    private static final String AGG_ERROR_TYPES = "types";

    /**
     * 关键字检索的字段与权重。正文最可信，堆栈次之，
     * 追踪 id 也放进来——用户最常做的就是把 traceId 直接粘进搜索框。
     */
    private static final List<String> KEYWORD_FIELDS = List.of("message^3", "exception", "traceId^2");

    /**
     * elasticsearchClient，ES 客户端。
     */
    private final ElasticsearchClient elasticsearchClient;

    /**
     * naming，日志索引命名。
     */
    private final LogIndexNaming naming;

    /**
     * 分页检索。
     */
    @Override
    public LogSearchResponse search(LogSearchRequest request) {
        int from = Math.max(0, request.getPageNum() - 1) * request.getPageSize();
        if (from + request.getPageSize() > MAX_RESULT_WINDOW) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "from+size exceeds max result window " + MAX_RESULT_WINDOW + ", narrow the time range instead");
        }
        try {
            SearchRequest searchRequest = SearchRequest.of(builder -> builder.index(naming.readAlias()).from(from).size(request.getPageSize()).query(query(request)).sort(sort -> sort.field(field -> field.field("timestamp").order(SortOrder.Desc))).highlight(Highlight.of(highlight -> highlight.fields("message", HighlightField.of(field -> field)).fields("exception", HighlightField.of(field -> field)))));
            SearchResponse<LogDocument> response = elasticsearchClient.search(searchRequest, LogDocument.class);
            LogSearchResponse result = new LogSearchResponse();
            result.setTotal(response.hits().total() == null ? 0L : response.hits().total().value());
            result.setPageNum(request.getPageNum());
            result.setPageSize(request.getPageSize());
            result.setList(toHits(response.hits().hits()));
            return result;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            if (isIndexMissing(ex)) {
                return emptyResult(request);
            }
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "log search failed", ex);
        }
    }

    /**
     * 仪表盘聚合。
     */
    @Override
    public LogOverviewResponse overview(LogSearchRequest request) {
        try {
            SearchRequest searchRequest = SearchRequest.of(builder -> builder.index(naming.readAlias()).size(0).query(query(request)).aggregations(AGG_LEVELS, aggregation -> aggregation.terms(terms -> terms.field("level").size(MAX_TERMS))).aggregations(AGG_TIMELINE, aggregation -> aggregation.autoDateHistogram(histogram -> histogram.field("timestamp").buckets(TIMELINE_BUCKETS)).aggregations(AGG_TIMELINE_LEVELS, sub -> sub.terms(terms -> terms.field("level").size(MAX_TERMS)))).aggregations(AGG_LOGGERS, aggregation -> aggregation.terms(terms -> terms.field("logger").size(MAX_TERMS))).aggregations(AGG_ERRORS, aggregation -> aggregation.filter(filter -> filter.term(term -> term.field("level").value("ERROR"))).aggregations(AGG_ERROR_TYPES, sub -> sub.terms(terms -> terms.field("exceptionType").size(MAX_TERMS)))));
            SearchResponse<LogDocument> response = elasticsearchClient.search(searchRequest, LogDocument.class);
            Map<String, Aggregate> aggregations = response.aggregations() == null ? Map.of() : response.aggregations();

            LogOverviewResponse result = new LogOverviewResponse();
            result.setTotal(response.hits().total() == null ? 0L : response.hits().total().value());
            result.setLevelCounts(toCounts(aggregations.get(AGG_LEVELS)));
            result.setTimeline(toTimeline(aggregations.get(AGG_TIMELINE)));
            result.setTopLoggers(toCounts(aggregations.get(AGG_LOGGERS)));
            result.setTopErrorTypes(Map.of());
            Aggregate errors = aggregations.get(AGG_ERRORS);
            if (errors != null && errors.isFilter()) {
                result.setErrorCount(errors.filter().docCount());
                Map<String, Aggregate> sub = errors.filter().aggregations();
                result.setTopErrorTypes(toCounts(sub == null ? null : sub.get(AGG_ERROR_TYPES)));
            }
            return result;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            if (isIndexMissing(ex)) {
                return emptyOverview();
            }
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "log overview failed", ex);
        }
    }

    /**
     * 上下文查询。
     */
    @Override
    public LogContextResponse context(String id, int before, int after) {
        LogSearchResponse.Hit target = fetch(id);
        LogContextResponse response = new LogContextResponse();
        response.setTarget(target);
        // 前面的按时间倒序取回来再反转，拿到的才是「紧贴目标之前的 N 条」
        response.setBefore(around(target.getTimestamp(), before, SortOrder.Desc, true));
        response.setAfter(around(target.getTimestamp(), after, SortOrder.Asc, false));
        return response;
    }

    /**
     * 按 id 取一条日志。
     *
     * @param id 文档 id
     * @return 日志
     */
    private LogSearchResponse.Hit fetch(String id) {
        try {
            var response = elasticsearchClient.get(request -> request.index(naming.readAlias()).id(id), LogDocument.class);
            if (!response.found() || response.source() == null) {
                throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "log not found");
            }
            return toHit(id, response.source(), Map.of());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            if (isIndexMissing(ex)) {
                throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "log not found");
            }
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "get log failed", ex);
        }
    }

    /**
     * 取目标时刻之前或之后的若干条。
     *
     * <p>用「时间小于/大于目标时刻」而不是分页偏移：前后各 N 条的定义就是时间上的邻近，
     * 偏移在日志这种持续写入的数据上是不稳定的——刚插入一条，偏移就全变了。
     *
     * @param timestamp 目标时刻
     * @param size      条数
     * @param order     取数方向
     * @param reverse   是否把结果反转成时间正序
     * @return 日志列表
     */
    private List<LogSearchResponse.Hit> around(Instant timestamp, int size, SortOrder order, boolean reverse) {
        if (size <= 0 || timestamp == null) {
            return List.of();
        }
        try {
            DateRangeQuery.Builder range = new DateRangeQuery.Builder().field("timestamp");
            String value = timestamp.toString();
            if (order == SortOrder.Asc) {
                range.gt(value);
            } else {
                range.lt(value);
            }
            Query query = new Query.Builder().range(builder -> builder.date(range.build())).build();
            SearchRequest searchRequest = SearchRequest.of(builder -> builder.index(naming.readAlias()).size(size).query(query).sort(sort -> sort.field(field -> field.field("timestamp").order(order))));
            SearchResponse<LogDocument> response = elasticsearchClient.search(searchRequest, LogDocument.class);
            List<LogSearchResponse.Hit> hits = toHits(response.hits().hits());
            if (!reverse) {
                return hits;
            }
            List<LogSearchResponse.Hit> ascending = new ArrayList<>(hits);
            Collections.reverse(ascending);
            return ascending;
        } catch (Exception ex) {
            if (!isIndexMissing(ex)) {
                log.warn("log context query failed: {}", ex.getMessage());
            }
            return List.of();
        }
    }

    /**
     * 拼检索条件。关键字进 must 参与打分，其余进 filter 只做筛选。
     *
     * @param request 检索条件
     * @return 查询
     */
    private Query query(LogSearchRequest request) {
        BoolQuery.Builder bool = new BoolQuery.Builder();
        String keyword = trimToNull(request.getKeyword());
        if (keyword != null) {
            bool.must(MultiMatchQuery.of(match -> match.fields(KEYWORD_FIELDS).query(keyword))._toQuery());
        }
        List<FieldValue> levels = levelsOf(request.getLevels());
        if (!levels.isEmpty()) {
            bool.filter(TermsQuery.of(terms -> terms.field("level").terms(field -> field.value(levels)))._toQuery());
        }
        String loggerPrefix = trimToNull(request.getLoggerPrefix());
        if (loggerPrefix != null) {
            bool.filter(PrefixQuery.of(prefix -> prefix.field("logger").value(loggerPrefix))._toQuery());
        }
        String thread = trimToNull(request.getThread());
        if (thread != null) {
            bool.filter(TermQuery.of(term -> term.field("thread").value(thread))._toQuery());
        }
        String traceId = trimToNull(request.getTraceId());
        if (traceId != null) {
            bool.filter(TermQuery.of(term -> term.field("traceId").value(traceId))._toQuery());
        }
        String host = trimToNull(request.getHost());
        if (host != null) {
            bool.filter(TermQuery.of(term -> term.field("host").value(host))._toQuery());
        }
        Long from = request.getFromMillis();
        Long to = request.getToMillis();
        if (from != null && to != null && from > to) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "fromMillis must not be greater than toMillis");
        }
        if (from != null || to != null) {
            bool.filter(RangeQuery.of(range -> range.date(date -> date.field("timestamp").gte(from == null ? null : Instant.ofEpochMilli(from).toString()).lte(to == null ? null : Instant.ofEpochMilli(to).toString())))._toQuery());
        }
        // 一个条件都没有时返回空的 bool，语义等价于 match_all
        return new Query.Builder().bool(bool.build()).build();
    }

    /**
     * 规范化级别入参：转大写去空白，丢掉空值。
     * 前端传 info 查不出东西是最常见的一类「看起来像没数据」的假故障。
     *
     * @param levels 级别列表
     * @return 规范化后的值
     */
    private List<FieldValue> levelsOf(List<String> levels) {
        if (levels == null || levels.isEmpty()) {
            return List.of();
        }
        List<FieldValue> values = new ArrayList<>(levels.size());
        for (String level : levels) {
            if (level == null || level.isBlank()) {
                continue;
            }
            values.add(FieldValue.of(level.trim().toUpperCase()));
        }
        return values;
    }

    /**
     * 转成响应里的日志列表。
     *
     * @param hits ES 返回的命中
     * @return 日志列表
     */
    private List<LogSearchResponse.Hit> toHits(List<Hit<LogDocument>> hits) {
        List<LogSearchResponse.Hit> results = new ArrayList<>(hits.size());
        for (Hit<LogDocument> hit : hits) {
            if (hit.source() == null) {
                continue;
            }
            Map<String, List<String>> highlight = hit.highlight() == null ? Map.of() : hit.highlight();
            results.add(toHit(hit.id(), hit.source(), highlight));
        }
        return results;
    }

    /**
     * 转成响应里的一条日志。
     *
     * @param id        文档 id
     * @param source    文档
     * @param highlight 高亮片段
     * @return 日志
     */
    private LogSearchResponse.Hit toHit(String id, LogDocument source, Map<String, List<String>> highlight) {
        LogSearchResponse.Hit hit = new LogSearchResponse.Hit();
        hit.setId(id);
        hit.setTimestamp(source.getTimestamp());
        hit.setLevel(source.getLevel());
        hit.setLogger(source.getLogger());
        hit.setThread(source.getThread());
        hit.setMessage(source.getMessage());
        hit.setExceptionType(source.getExceptionType());
        hit.setException(source.getException());
        hit.setTraceId(source.getTraceId());
        hit.setHost(source.getHost());
        hit.setMessageHighlights(highlight.getOrDefault("message", List.of()));
        hit.setExceptionHighlights(highlight.getOrDefault("exception", List.of()));
        return hit;
    }

    /**
     * 把 terms 聚合转成计数表。
     *
     * @param aggregate 聚合结果
     * @return 桶名到条数
     */
    private Map<String, Long> toCounts(Aggregate aggregate) {
        if (aggregate == null || !aggregate.isSterms()) {
            return Map.of();
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (StringTermsBucket bucket : aggregate.sterms().buckets().array()) {
            counts.put(bucket.key().stringValue(), bucket.docCount());
        }
        return counts;
    }

    /**
     * 把 auto_date_histogram 转成时间轴。
     *
     * @param aggregate 聚合结果
     * @return 时间桶
     */
    private List<LogOverviewResponse.TimelineBucket> toTimeline(Aggregate aggregate) {
        if (aggregate == null || !aggregate.isAutoDateHistogram()) {
            return List.of();
        }
        List<LogOverviewResponse.TimelineBucket> buckets = new ArrayList<>();
        for (DateHistogramBucket bucket : aggregate.autoDateHistogram().buckets().array()) {
            LogOverviewResponse.TimelineBucket item = new LogOverviewResponse.TimelineBucket();
            item.setTimeMillis(bucket.key());
            item.setTotal(bucket.docCount());
            Map<String, Aggregate> sub = bucket.aggregations();
            item.setLevelCounts(toCounts(sub == null ? null : sub.get(AGG_TIMELINE_LEVELS)));
            buckets.add(item);
        }
        return buckets;
    }

    /**
     * 是否索引还不存在。
     *
     * @param ex 异常
     * @return 索引缺失返回 true
     */
    private boolean isIndexMissing(Exception ex) {
        return ex instanceof ElasticsearchException elasticsearchException && elasticsearchException.status() == 404;
    }

    /**
     * 空检索结果。
     *
     * @param request 检索条件
     * @return 空结果
     */
    private LogSearchResponse emptyResult(LogSearchRequest request) {
        LogSearchResponse result = new LogSearchResponse();
        result.setTotal(0L);
        result.setPageNum(request.getPageNum());
        result.setPageSize(request.getPageSize());
        result.setList(List.of());
        return result;
    }

    /**
     * 空仪表盘。
     *
     * @return 空仪表盘
     */
    private LogOverviewResponse emptyOverview() {
        LogOverviewResponse result = new LogOverviewResponse();
        result.setTotal(0L);
        result.setLevelCounts(Map.of());
        result.setTimeline(List.of());
        result.setTopLoggers(Map.of());
        result.setTopErrorTypes(Map.of());
        result.setErrorCount(0L);
        return result;
    }

    /**
     * 去空白，空串转 null。
     *
     * @param value 原值
     * @return 处理后的值
     */
    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

}
