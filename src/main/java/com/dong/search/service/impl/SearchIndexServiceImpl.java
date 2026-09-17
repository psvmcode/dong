package com.dong.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.search.config.IndexNameResolver;
import com.dong.search.dto.RebuildResponse;
import com.dong.search.service.SearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
/**
 * 索引治理实现。
 *
 * <p>核心是别名这一层：应用读写的是别名 dong_lab_product，
 * 真实索引是 dong_lab_product_v1、v2…… 切换只改别名指向，
 * 所以重建索引不用改代码、不用停服务、也不用担心读写写到半新半旧的两份索引上。
 *
 * <p>mapping 全部显式声明，一个字段都不能交给 ES 动态推断：
 * 字符串默认会推断成 text，而 text 字段做 terms 聚合会直接报错；
 * 中文还要显式指定 IK 分词器，否则整串当成一个词，只能全等匹配。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "dong.elasticsearch", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class SearchIndexServiceImpl implements SearchIndexService {

    /**
     * 写入时分词器：细粒度切分，尽量多切出组合，用户换个说法来查也能命中。
     */
    private static final String ANALYZER_INDEX = "ik_max_word";

    /**
     * 查询时分词器：粗粒度切分，避免把用户的查询词切得太碎导致召回跑偏。
     */
    private static final String ANALYZER_SEARCH = "ik_smart";

    /**
     * 带同义词的查询分词器。同义词只在查询时展开，不在写入时展开——
     * 写入时展开会让索引膨胀，而且改同义词就得重建索引，代价高一个数量级。
     */
    private static final String ANALYZER_SEARCH_SYNONYM = "ik_smart_synonym";

    /**
     * 同义词过滤器名。
     */
    private static final String SYNONYM_FILTER = "ik_synonym";

    /**
     * 同义词规则。一行一组，组内词互为同义词，用英文逗号分隔。
     *
     * <p>这里直接内联在索引设置里，改规则要重建索引才生效（改 analyzer 属于改 settings）。
     * 数据量大了应该换成同义词文件放磁盘，让 ES 定时热加载。
     */
    private static final List<String> SYNONYM_RULES = List.of(
            "手机,智能手机,移动电话",
            "电脑,计算机,笔记本",
            "云服务器,云服务,ecs");

    /**
     * 索引里的日期格式，接受 ISO 时间或时间戳两种写法。
     */
    private static final String DATE_FORMAT = "strict_date_optional_time||epoch_millis";

    /**
     * 起手版本号。
     */
    private static final int FIRST_VERSION = 1;

    /**
     * elasticsearchClient，ES 客户端。
     */
    private final ElasticsearchClient elasticsearchClient;

    /**
     * indexNameResolver，索引名解析器。
     */
    private final IndexNameResolver indexNameResolver;

    /**
     * 确保别名可用。
     */
    @Override
    public void ensureAlias() {
        String alias = indexNameResolver.resolve(IndexNameResolver.PRODUCT_INDEX);
        try {
            if (elasticsearchClient.indices().existsAlias(request -> request.name(alias)).value()) {
                log.info("alias {} already exists, index left untouched", alias);
                return;
            }
            String target = indexNameResolver.versioned(IndexNameResolver.PRODUCT_INDEX, FIRST_VERSION);
            if (elasticsearchClient.indices().exists(request -> request.index(alias)).value()) {
                migrateLegacyIndex(alias, target);
            } else if (!elasticsearchClient.indices().exists(request -> request.index(target)).value()) {
                createIndex(target);
            }
            elasticsearchClient.indices().putAlias(request -> request.index(target).name(alias));
            log.info("alias {} now points to {}", alias, target);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to prepare index alias " + alias, ex);
        }
    }

    /**
     * 当前别名指向的真实索引名。
     */
    @Override
    public String currentIndex() {
        String alias = indexNameResolver.resolve(IndexNameResolver.PRODUCT_INDEX);
        try {
            var response = elasticsearchClient.indices().getAlias(request -> request.name(alias));
            return response.result().keySet().stream()
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE,
                            "alias " + alias + " points to no index"));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "resolve alias failed", ex);
        }
    }

    /**
     * 零停机重建。
     */
    @Override
    public RebuildResponse rebuild() {
        String alias = indexNameResolver.resolve(IndexNameResolver.PRODUCT_INDEX);
        String current = currentIndex();
        String next = indexNameResolver.versioned(IndexNameResolver.PRODUCT_INDEX,
                indexNameResolver.versionOf(current) + 1);
        try {
            createIndex(next);
            var reindex = elasticsearchClient.reindex(request -> request
                    .source(source -> source.index(current))
                    .dest(dest -> dest.index(next))
                    .refresh(true));
            elasticsearchClient.indices().updateAliases(request -> request
                    .actions(action -> action.remove(remove -> remove.index(current).alias(alias)))
                    .actions(action -> action.add(add -> add.index(next).alias(alias))));
            elasticsearchClient.indices().delete(request -> request.index(current));
            log.info("index rebuilt: {} -> {} (alias {}, {} docs moved)", current, next, alias, reindex.total());

            RebuildResponse response = new RebuildResponse();
            response.setAlias(alias);
            response.setFromIndex(current);
            response.setToIndex(next);
            response.setMovedDocs(reindex.total() == null ? 0L : reindex.total());
            return response;
        } catch (Exception ex) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "rebuild failed", ex);
        }
    }

    /**
     * 迁移老部署：ES 里存在一个和别名同名的真实索引，先用 reindex 搬到 v1 再挂别名。
     * 不这么做的话别名会和索引同名，ES 直接拒绝创建。
     */
    private void migrateLegacyIndex(String legacyIndex, String target) throws IOException {
        if (!elasticsearchClient.indices().exists(request -> request.index(target)).value()) {
            createIndex(target);
            var reindex = elasticsearchClient.reindex(request -> request
                    .source(source -> source.index(legacyIndex))
                    .dest(dest -> dest.index(target))
                    .refresh(true));
            log.info("migrated {} docs from legacy index {} to {}", reindex.total(), legacyIndex, target);
        }
        elasticsearchClient.indices().delete(request -> request.index(legacyIndex));
    }

    /**
     * 建索引。1 分片 0 副本：这套 ES 是单节点，副本数大于 0 会一直 unassigned，
     * 索引健康度永远进不了 green；单节点下副本本来也没有可用性收益。
     */
    private void createIndex(String index) throws IOException {
        elasticsearchClient.indices().create(request -> request
                .index(index)
                .mappings(mapping())
                .settings(settings()));
        log.info("index {} created with ik analyzer mapping", index);
    }

    /**
     * 索引映射。字段类型逐个写死，见类注释。
     */
    private TypeMapping mapping() {
        return TypeMapping.of(builder -> builder
                .properties("id", Property.of(property -> property.keyword(keyword -> keyword)))
                .properties("name", Property.of(property -> property.text(text -> text
                        .analyzer(ANALYZER_INDEX)
                        .searchAnalyzer(ANALYZER_SEARCH_SYNONYM))))
                .properties("category", Property.of(property -> property.keyword(keyword -> keyword)))
                .properties("description", Property.of(property -> property.text(text -> text
                        .analyzer(ANALYZER_INDEX)
                        .searchAnalyzer(ANALYZER_SEARCH))))
                .properties("price", Property.of(property -> property.double_(number -> number)))
                .properties("stock", Property.of(property -> property.integer(number -> number)))
                .properties("status", Property.of(property -> property.keyword(keyword -> keyword)))
                .properties("suggest", Property.of(property -> property.completion(completion -> completion)))
                .properties("location", Property.of(property -> property.geoPoint(geoPoint -> geoPoint)))
                .properties("createTime", Property.of(property -> property.date(date -> date.format(DATE_FORMAT)))));
    }

    /**
     * 索引设置。同义词只在查询时展开，见常量注释。
     */
    private IndexSettings settings() {
        return IndexSettings.of(builder -> builder
                .numberOfShards("1")
                .numberOfReplicas("0")
                .analysis(analysis -> analysis
                        .filter(SYNONYM_FILTER, filter -> filter.definition(definition -> definition
                                .synonymGraph(synonym -> synonym.lenient(true).synonyms(SYNONYM_RULES))))
                        .analyzer(ANALYZER_SEARCH_SYNONYM, analyzer -> analyzer.custom(custom -> custom
                                .tokenizer(ANALYZER_SEARCH)
                                .filter("lowercase", SYNONYM_FILTER)))));
    }

}
