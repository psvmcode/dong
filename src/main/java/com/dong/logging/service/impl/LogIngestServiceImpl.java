package com.dong.logging.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.logging.config.LogIndexNaming;
import com.dong.logging.dto.LogPipelineStats;
import com.dong.logging.entity.LogDocument;
import com.dong.logging.service.LogIngestService;
import com.dong.logging.support.LogEventBuffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 日志采集与写入实现。
 *
 * <p>三条贯穿全局的取舍：
 * <ul>
 *   <li><b>索引失败不重试、不回滚。</b>刷写失败时这一批已经从缓冲取出来了，
 *       再放回去只会让下一次刷写更有可能失败。日志是尽力而为的数据，
 *       故障期间丢一批的代价，远小于让缓冲无限增长把内存吃掉。</li>
 *   <li><b>建索引失败要退避。</b>ES 不可用时不能每秒重试一次，
 *       那会把一次中间件故障放大成持续的错误风暴。退避期间直接快速失败。</li>
 *   <li><b>启动期不强依赖 ES。</b>索引没准备好只是记一条警告，
 *       绝不让「日志模块不可用」把整个应用拖到起不来——
 *       这是与 search 模块最大的区别：那边索引不可用等于功能不可用，
 *       这边日志不可用只等于少一份可观测数据。</li>
 * </ul>
 *
 * <p>写入目标按日志时间算，而不是一律写今天：回填历史日志时按各自日期归档，
 * 「保留 N 天」才不会把整段历史一次性判死。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"dong.logging.enabled", "dong.elasticsearch.enabled"}, havingValue = "true")
public class LogIngestServiceImpl implements LogIngestService {

    /**
     * 写入时分词器：尽量多切，用户换个说法来查也能命中。
     */
    private static final String ANALYZER_INDEX = "ik_max_word";

    /**
     * 查询时分词器：切得粗一点，避免把查询词切太碎导致召回跑偏。
     */
    private static final String ANALYZER_SEARCH = "ik_smart";

    /**
     * 日期格式，同时接受 ISO 时间与毫秒时间戳。
     */
    private static final String DATE_FORMAT = "strict_date_optional_time||epoch_millis";

    /**
     * 刷新间隔。日志对可见性延迟没有毫秒级要求，
     * 5 秒换来的是段合并压力小一个量级——日志写入是持续的高吞吐动作。
     */
    private static final String REFRESH_INTERVAL = "5s";

    /**
     * 单个 bulk 请求的条数。再大就会让一次请求的 body 过大，超时与重试成本都上升。
     */
    private static final int BULK_CHUNK = 500;

    /**
     * 新建批次的初始容量提示。
     */
    private static final int BATCH_HINT = 256;

    /**
     * elasticsearchClient，ES 客户端。
     */
    private final ElasticsearchClient elasticsearchClient;

    /**
     * naming，日志索引命名。
     */
    private final LogIndexNaming naming;

    /**
     * 建索引失败后的退避时长，毫秒。
     */
    @Value("${dong.logging.retry-backoff-ms:10000}")
    private long retryBackoffMs;

    /**
     * 保留天数，含今天。
     */
    @Value("${dong.logging.retention-days:14}")
    private int retentionDays;

    /**
     * 当前写入的索引。跨天时会被下一次 ensure 换掉。
     */
    private final AtomicReference<String> writeIndex = new AtomicReference<>();

    /**
     * 下一次允许重试建索引的时间戳。
     */
    private final AtomicLong nextEnsureAt = new AtomicLong();

    /**
     * 累计写入条数。
     */
    private final AtomicLong indexedDocs = new AtomicLong();

    /**
     * 失败批次数。
     */
    private final AtomicLong failedBatches = new AtomicLong();

    /**
     * 最后一次成功刷写的时间。
     */
    private volatile long lastFlushAt;

    /**
     * 最后一次成功刷写的条数。
     */
    private volatile int lastBatchSize;

    /**
     * 最后一次出错的时间。
     */
    private volatile long lastErrorAt;

    /**
     * 最后一次出错的原因。
     */
    private volatile String lastError;

    /**
     * 确保今天要写的索引就绪。
     */
    @Override
    public String ensureWriteIndex() {
        String today = naming.todayIndex();
        if (today.equals(writeIndex.get())) {
            return today;
        }
        long now = System.currentTimeMillis();
        if (now < nextEnsureAt.get()) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "log index is not ready, backing off");
        }
        try {
            String index = ensureIndex(today);
            writeIndex.set(index);
            return index;
        } catch (BusinessException ex) {
            nextEnsureAt.set(now + retryBackoffMs);
            lastErrorAt = now;
            lastError = ex.getMessage();
            throw ex;
        }
    }

    /**
     * 确保指定索引就绪。
     */
    @Override
    public String ensureIndex(String index) {
        try {
            if (!elasticsearchClient.indices().exists(request -> request.index(index)).value()) {
                createIndex(index);
            }
            ensureReadAlias(index);
            return index;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "prepare log index failed", ex);
        }
    }

    /**
     * 把缓冲里的日志刷进 ES。
     */
    @Override
    public int flush(int maxBatch) {
        LogEventBuffer buffer = LogEventBuffer.instance();
        if (buffer.size() == 0) {
            return 0;
        }
        // 先确保索引可用再取数据：取出来写不进去就等于丢了这一批
        String index = ensureWriteIndex();
        List<LogDocument> batch = new ArrayList<>(Math.min(maxBatch, BATCH_HINT));
        int drained = buffer.drain(batch, maxBatch);
        if (drained == 0) {
            return 0;
        }
        bulkIndex(index, batch);
        indexedDocs.addAndGet(drained);
        lastBatchSize = drained;
        lastFlushAt = System.currentTimeMillis();
        lastError = null;
        return drained;
    }

    /**
     * 批量写入指定索引。
     */
    @Override
    public void bulkIndex(String index, List<LogDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        for (int from = 0; from < documents.size(); from += BULK_CHUNK) {
            writeChunk(index, documents.subList(from, Math.min(from + BULK_CHUNK, documents.size())));
        }
    }

    /**
     * 刷新读别名。
     */
    @Override
    public void refresh() {
        if (listIndices().isEmpty()) {
            return;
        }
        try {
            elasticsearchClient.indices().refresh(request -> request.index(naming.readAlias()));
        } catch (Exception ex) {
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "refresh failed", ex);
        }
    }

    /**
     * 删除超出保留天数的索引。
     */
    @Override
    public int cleanupExpired() {
        // 保留 N 天含今天，所以最后一天是 today - (N-1)
        LocalDate cutoff = naming.today().minusDays(Math.max(0, retentionDays - 1));
        int deleted = 0;
        for (String index : listIndices()) {
            LocalDate date = naming.dateOf(index);
            if (date == null || !date.isBefore(cutoff)) {
                continue;
            }
            try {
                elasticsearchClient.indices().delete(request -> request.index(index));
                deleted++;
                log.info("log index {} deleted, earlier than {}", index, cutoff);
            } catch (Exception ex) {
                log.warn("delete log index {} failed: {}", index, ex.getMessage());
            }
        }
        if (deleted > 0 && writeIndex.get() != null && !listIndices().contains(writeIndex.get())) {
            writeIndex.set(null);
        }
        return deleted;
    }

    /**
     * 采集管道的运行状态。
     */
    @Override
    public LogPipelineStats stats() {
        LogEventBuffer buffer = LogEventBuffer.instance();
        LogPipelineStats stats = new LogPipelineStats();
        stats.setBufferCapacity(buffer.capacity());
        stats.setBufferSize(buffer.size());
        stats.setBufferUsage(buffer.usage());
        stats.setReceived(buffer.received());
        stats.setDropped(buffer.dropped());
        stats.setDroppedByWaterMark(buffer.droppedByWaterMark());
        stats.setIndexedDocs(indexedDocs.get());
        stats.setFailedBatches(failedBatches.get());
        stats.setLastFlushAt(lastFlushAt);
        stats.setLastBatchSize(lastBatchSize);
        stats.setLastErrorAt(lastErrorAt);
        stats.setLastError(lastError);
        stats.setReadAlias(naming.readAlias());
        stats.setWriteIndex(writeIndex.get());
        stats.setIndices(listIndices());
        stats.setDocumentCount(countDocuments());
        stats.setRetentionDays(retentionDays);
        return stats;
    }

    /**
     * 读别名背后的全部索引。
     */
    @Override
    public List<String> listIndices() {
        return aliasedIndices(naming.readAlias()).stream().sorted().toList();
    }

    /**
     * 写一个 bulk 分片并检查失败项。
     *
     * <p>bulk 接口即使单条失败也返回 200，只有 errors 标志为 true 才说明真失败了。
     * 不检查的话，mapping 不匹配这类问题会静默吞掉整批日志。
     *
     * @param index 索引名
     * @param chunk 本批日志
     */
    private void writeChunk(String index, List<LogDocument> chunk) {
        try {
            List<BulkOperation> operations = chunk.stream().map(document -> new BulkOperation.Builder().index(operation -> operation.index(index).document(document)).build()).toList();
            BulkResponse response = elasticsearchClient.bulk(builder -> builder.index(index).operations(operations));
            if (!Boolean.TRUE.equals(response.errors())) {
                return;
            }
            String reason = response.items() == null ? "unknown" : response.items().stream().filter(item -> item.error() != null).limit(3).map(item -> item.error().reason()).reduce((first, second) -> first + "; " + second).orElse("unknown");
            log.error("log bulk request reported errors: {}", reason);
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "bulk index failed: " + reason);
        } catch (BusinessException ex) {
            markFailure(ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            markFailure(ex.getMessage());
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "bulk index failed", ex);
        }
    }

    /**
     * 记录一次失败。
     *
     * @param message 失败原因
     */
    private void markFailure(String message) {
        failedBatches.incrementAndGet();
        lastErrorAt = System.currentTimeMillis();
        lastError = message;
    }

    /**
     * 建索引。1 分片 0 副本：单节点 ES 上副本只会一直 unassigned，
     * 索引永远进不了 green，而且单节点下副本也没有可用性收益。
     *
     * @param index 索引名
     * @throws IOException ES 调用失败
     */
    private void createIndex(String index) throws IOException {
        elasticsearchClient.indices().create(request -> request.index(index).mappings(mapping()).settings(settings()));
        log.info("log index {} created", index);
    }

    /**
     * 把索引挂到读别名上。
     *
     * @param index 索引名
     * @throws IOException ES 调用失败
     */
    private void ensureReadAlias(String index) throws IOException {
        String alias = naming.readAlias();
        if (aliasedIndices(alias).contains(index)) {
            return;
        }
        elasticsearchClient.indices().putAlias(request -> request.index(index).name(alias));
        log.info("alias {} now covers {}", alias, index);
    }

    /**
     * 别名背后的索引。别名不存在时 ES 返回 404，这是正常路径不是错误。
     *
     * @param alias 别名
     * @return 索引名集合
     */
    private Set<String> aliasedIndices(String alias) {
        try {
            return elasticsearchClient.indices().getAlias(request -> request.name(alias)).result().keySet();
        } catch (Exception ex) {
            return Set.of();
        }
    }

    /**
     * 索引里的日志总数。
     *
     * @return 条数，取不到返回 0
     */
    private long countDocuments() {
        try {
            return elasticsearchClient.count(request -> request.index(naming.readAlias())).count();
        } catch (Exception ex) {
            return 0L;
        }
    }

    /**
     * 日志索引映射。字段类型逐个写死，一个都不交给动态推断：
     * 字符串默认会被推断成 text，而 text 字段没有 doc_values，
     * 对它做 terms 聚合会直接报错，且改不回来。
     *
     * @return 映射
     */
    private TypeMapping mapping() {
        return TypeMapping.of(builder -> builder.properties("timestamp", Property.of(property -> property.date(date -> date.format(DATE_FORMAT)))).properties("level", Property.of(property -> property.keyword(keyword -> keyword))).properties("logger", Property.of(property -> property.keyword(keyword -> keyword))).properties("thread", Property.of(property -> property.keyword(keyword -> keyword))).properties("message", Property.of(property -> property.text(text -> text.analyzer(ANALYZER_INDEX).searchAnalyzer(ANALYZER_SEARCH)))).properties("exceptionType", Property.of(property -> property.keyword(keyword -> keyword))).properties("exception", Property.of(property -> property.text(text -> text.analyzer(ANALYZER_INDEX).searchAnalyzer(ANALYZER_SEARCH)))).properties("traceId", Property.of(property -> property.keyword(keyword -> keyword))).properties("host", Property.of(property -> property.keyword(keyword -> keyword))).properties("app", Property.of(property -> property.keyword(keyword -> keyword))));
    }

    /**
     * 索引设置。
     *
     * @return 设置
     */
    private IndexSettings settings() {
        return IndexSettings.of(builder -> builder.numberOfShards("1").numberOfReplicas("0").refreshInterval(time -> time.time(REFRESH_INTERVAL)));
    }

}
