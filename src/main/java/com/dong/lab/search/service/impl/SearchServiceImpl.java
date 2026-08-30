package com.dong.lab.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.NumberRangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import com.dong.lab.search.config.IndexNameResolver;
import com.dong.lab.search.dto.ProductSearchRequest;
import com.dong.lab.search.dto.ProductSearchResponse;
import com.dong.lab.search.entity.ProductDocument;
import com.dong.lab.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "lab.elasticsearch", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final ElasticsearchClient elasticsearchClient;

    private final IndexNameResolver indexNameResolver;

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
            if (Boolean.TRUE.equals(response.errors())) {
                String reason = response.items().stream()
                        .filter(item -> item.error() != null)
                        .map(item -> item.id() + ": " + item.error().reason())
                        .limit(3)
                        .reduce((first, second) -> first + "; " + second)
                        .orElse("unknown");
                log.error("bulk index reported errors: {}", reason);
                throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "bulk index failed: " + reason);
            }

            log.info("bulk indexed {} documents", list.size());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "bulk index failed", ex);
        }
    }

    @Override
    public void deleteById(String id) {
        try {
            elasticsearchClient.delete(builder -> builder.index(indexName()).id(id));
        } catch (Exception ex) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "delete failed", ex);
        }
    }

    @Override
    public ProductSearchResponse search(ProductSearchRequest request) {
        try {
            BoolQuery.Builder bool = new BoolQuery.Builder();
            if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
                bool.must(MatchQuery.of(match -> match
                        .field("name")
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

            int from = Math.max(0, request.getPageNum() - 1) * request.getPageSize();
            SearchRequest searchRequest = SearchRequest.of(builder -> builder
                    .index(indexName())
                    .from(from)
                    .size(request.getPageSize())
                    .query(new Query.Builder().bool(bool.build()).build())
                    .highlight(Highlight.of(highlight -> highlight
                            .fields("name", HighlightField.of(field -> field))))
                    .aggregations("categoryFacets", aggregation -> aggregation
                            .terms(terms -> terms.field("category"))));
            SearchResponse<ProductDocument> response =
                    elasticsearchClient.search(searchRequest, ProductDocument.class);
            ProductSearchResponse result = new ProductSearchResponse();
            result.setTotal(response.hits().total() == null ? 0L : response.hits().total().value());
            result.setPageNum(request.getPageNum());
            result.setPageSize(request.getPageSize());
            result.setList(toHits(response.hits().hits()));
            result.setCategoryFacets(toFacets(response.aggregations() == null
                    ? Map.of() : response.aggregations()));
            return result;
        } catch (Exception ex) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "search failed", ex);
        }
    }

    @Override
    public long count() {
        try {
            CountRequest request = CountRequest.of(builder -> builder.index(indexName()));
            return elasticsearchClient.count(request).count();
        } catch (Exception ex) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "count failed", ex);
        }
    }

    private List<ProductSearchResponse.Hit> toHits(List<Hit<ProductDocument>> hits) {
        List<ProductSearchResponse.Hit> results = new ArrayList<>(hits.size());
        for (Hit<ProductDocument> hit : hits) {
            ProductDocument source = hit.source();
            if (source == null) {
                continue;
            }
            ProductSearchResponse.Hit item = new ProductSearchResponse.Hit();
            item.setId(hit.id());
            item.setName(source.getName());
            item.setCategory(source.getCategory());
            item.setPrice(source.getPrice());
            item.setStock(source.getStock());
            item.setHighlight(hit.highlight() == null ? List.of() : hit.highlight().getOrDefault("name", List.of()));
            results.add(item);
        }
        return results;
    }

    private Map<String, Long> toFacets(Map<String, Aggregate> aggregations) {
        if (aggregations == null || aggregations.isEmpty()) {
            return Map.of();
        }
        Aggregate facet = aggregations.get("categoryFacets");
        if (facet == null || facet.sterms() == null) {
            return Map.of();
        }

        Map<String, Long> result = new LinkedHashMap<>();
        for (var bucket : facet.sterms().buckets().array()) {
            result.put(bucket.key().stringValue(), bucket.docCount());
        }
        return result;
    }

    private String indexName() {
        return indexNameResolver.resolve("product");
    }

}
