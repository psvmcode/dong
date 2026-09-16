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
 * 商品索引初始化器。应用启动时确认索引存在，且 mapping 就是下面代码里写死的那一套。
 *
 * <p>为什么不指望 ES 的动态映射自动建：ES 会把 JSON 里的字符串一律推断成 text，
 * 而 text 字段做 terms 聚合是非法操作，分面统计会直接报错；createTime 也必须显式声明成 date
 * 才能做范围查询和排序；中文不配置分词器则只能整串匹配。这些类型一旦被动态映射定下来就改不动了
 * （ES 不允许修改字段类型，只能 reindex），所以宁可在建索引这一步把类型钉死。
 *
 * <p>为什么是「存在就跳过」而不是每次覆盖：同样因为字段类型不可变，
 * 启动时无条件重建等于把索引连同数据一起删掉，只能幂等创建。代价是：
 * 在这里改了 mapping 之后，已经存在的索引不会自动跟着变，要手动 put mapping 或 reindex。
 *
 * <p>为什么是 1 分片 0 副本：这套 ES 是单节点，副本数只要大于 0 就会因为找不到第二个节点
 * 一直处于 unassigned，索引健康度永远进不了 green；单节点下副本本来也没有可用性收益。
 *
 * <p>建不出来就抛异常让应用启动失败：索引缺失等于整个 search 模块不可用，
 * 与其带着残缺状态启动、等第一次写入才报错，不如在启动期就暴露出来。
 *
 * <p>局限：这是自包含的实验做法。生产一般不在应用里建索引，而是由 index template 加别名加 ILM
 * 在运维侧下发，应用只认别名，重建索引时既不用改代码也不用停机。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "dong.elasticsearch", name = "enabled", havingValue = "true")
@RequiredArgsConstructor

public class SearchIndexInitializer {

    /**
     * 写入时分词器：细粒度切分，一个词条尽量多切出几种组合，
     * 这样用户换个说法来查也能命中。
     */
    private static final String ANALYZER_INDEX = "ik_max_word";

    /**
     * 查询时分词器：粗粒度切分，避免把用户的查询词切得太碎导致召回跑偏。
     *
     * <p>写入细、查询粗是 IK 的常规搭配：建倒排时多切几刀扩大覆盖面，
     * 检索时按粗粒度还原用户的真实意图。
     */
    private static final String ANALYZER_SEARCH = "ik_smart";

    /**
     * ES 客户端，这里只用来判断索引是否存在以及创建索引。
     */
    private final ElasticsearchClient elasticsearchClient;

    /**
     * 索引名解析器，把逻辑名 product 拼成真实索引名，如 dong_lab_product。
     */
    private final IndexNameResolver indexNameResolver;

    /**
     * 启动时创建索引，幂等：索引已存在就原样返回，一个字段都不碰。
     *
     * <p>用 @PostConstruct 在启动期执行，而不是等第一次写入时惰性创建，
     * 是为了让「索引不存在」这类问题在启动阶段就暴露，而不是等到线上第一次搜索才发现。
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
