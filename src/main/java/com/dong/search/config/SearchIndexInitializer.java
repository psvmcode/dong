package com.dong.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
/**
 * SearchIndexInitializer。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "dong.elasticsearch", name = "enabled", havingValue = "true")
@RequiredArgsConstructor

public class SearchIndexInitializer {

    private static final String ANALYZER_INDEX = "ik_max_word";

    private static final String ANALYZER_SEARCH = "ik_smart";

    /**
     * elasticsearchClient。
     */
    private final ElasticsearchClient elasticsearchClient;

    /**
     * indexNameResolver。
     */
    private final IndexNameResolver indexNameResolver;

    /**
     * createMapping。
     */
    @PostConstruct
    public void createMapping() {
        String index = indexNameResolver.resolve("product");
        try {
            if (elasticsearchClient.indices().exists(ExistsRequest.of(builder -> builder.index(index))).value()) {
                log.info("index {} already exists, mapping left untouched", index);
                return;
            }

            TypeMapping mapping = TypeMapping.of(builder -> builder
                    .properties("id", Property.of(property -> property.keyword(keyword -> keyword)))
                    .properties("name", Property.of(property -> property.text(
                            TextProperty.of(text -> text.analyzer(ANALYZER_INDEX).searchAnalyzer(ANALYZER_SEARCH)))))
                    .properties("category", Property.of(property -> property.keyword(keyword -> keyword)))
                    .properties("description", Property.of(property -> property.text(
                            TextProperty.of(text -> text.analyzer(ANALYZER_INDEX).searchAnalyzer(ANALYZER_SEARCH)))))
                    .properties("price", Property.of(property -> property.double_(number -> number)))
                    .properties("stock", Property.of(property -> property.integer(number -> number)))
                    .properties("status", Property.of(property -> property.keyword(keyword -> keyword)))
                    .properties("createTime", Property.of(property -> property.date(
                            date -> date.format("strict_date_optional_time||epoch_millis")))));
            elasticsearchClient.indices().create(CreateIndexRequest.of(builder -> builder
                    .index(index)
                    .mappings(mapping)
                    .settings(settings -> settings
                            .numberOfShards("1")
                            .numberOfReplicas("0"))));
            log.info("index {} created with ik analyzer mapping", index);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to create index " + index, ex);
        }
    }

}
