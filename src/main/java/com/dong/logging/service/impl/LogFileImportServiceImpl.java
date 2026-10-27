package com.dong.logging.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import com.dong.logging.config.LogIndexNaming;
import com.dong.logging.dto.LogImportResponse;
import com.dong.logging.dto.LogImportState;
import com.dong.logging.entity.LogDocument;
import com.dong.logging.service.LogFileImportService;
import com.dong.logging.service.LogIngestService;
import com.dong.logging.support.LogFileParser;
import com.dong.logging.support.LogHost;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * 历史日志回填实现。
 *
 * <p>只导两类文件：主日志 {@code {app}.log} 与它轮转出来的 {@code {app}.N.log.gz}。
 * error / warn / debug 那几个文件是主日志的子集，一起导就是成倍重复——
 * 日志检索里「同一条出现两次」比「少一条」更难发现，也更难解释。
 *
 * <p>超出保留窗口的日志直接跳过。回填不是归档：这些日志进了索引，
 * 下一次清理任务会立刻把它们删掉，白白占一轮磁盘与 IO。
 *
 * <p>每次调用的行数必须封顶，靠位点续传多跑几次补完。
 * 不封顶的话，一个跑了半年的日志目录能在一次调用里把内存和 ES 一起打满。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"dong.logging.enabled", "dong.elasticsearch.enabled"}, havingValue = "true")
public class LogFileImportServiceImpl implements LogFileImportService {

    /**
     * 单个 bulk 的条数。
     */
    private static final int BULK_CHUNK = 500;

    /**
     * 压缩文件后缀。
     */
    private static final String GZ_SUFFIX = ".gz";

    /**
     * 扫描深度。日志目录只有一层日期子目录，再深就是别的东西了。
     */
    private static final int SCAN_DEPTH = 2;

    /**
     * 轮转目录名的日期格式，与 logback 的 fileNamePattern 对齐。
     */
    private static final Pattern DATE_DIRECTORY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    /**
     * elasticsearchClient，ES 客户端。
     */
    private final ElasticsearchClient elasticsearchClient;

    /**
     * naming，日志索引命名。
     */
    private final LogIndexNaming naming;

    /**
     * ingestService，日志写入服务。
     */
    private final LogIngestService ingestService;

    /**
     * 日志目录。
     */
    @Value("${dong.logging.log-dir:logs}")
    private String logDir;

    /**
     * 应用名，决定要导哪些文件。
     */
    @Value("${spring.application.name:dong}")
    private String appName;

    /**
     * 保留天数，超出窗口的历史日志不导。
     */
    @Value("${dong.logging.retention-days:14}")
    private int retentionDays;

    /**
     * 状态索引是否已确认存在。
     */
    private volatile boolean metaReady;

    /**
     * 从日志目录回填。
     */
    @Override
    public LogImportResponse importFiles(int maxLines) {
        LogImportResponse response = new LogImportResponse();
        Path root = Paths.get(logDir);
        if (!Files.isDirectory(root)) {
            log.warn("log dir {} does not exist, nothing to import", root.toAbsolutePath());
            return response;
        }
        LocalDate cutoff = cutoffDate();
        ImportSink sink = new ImportSink(ingestService, naming, naming.dailyIndex(cutoff));
        long remaining = maxLines;
        int handled = 0;
        for (Path file : collectFiles(root, cutoff)) {
            if (remaining <= 0) {
                break;
            }
            long size = sizeOf(file);
            boolean compressed = file.getFileName().toString().endsWith(GZ_SUFFIX);
            LogImportState state = loadState(file);
            if (state != null && skip(state, size, compressed)) {
                continue;
            }
            handled++;
            long startLine = startLineOf(state, size, compressed);
            ReadResult result = readFile(file, startLine, remaining, sink);
            remaining -= result.lines;
            saveState(stateOf(file, startLine + result.lines, size, result.finished));
        }
        sink.flush();
        ingestService.refresh();
        response.setFiles(handled);
        response.setParsed(sink.parsed());
        response.setIndexed(sink.indexed());
        response.setSkipped(sink.skipped());
        log.info("log backfill finished: files={} parsed={} indexed={} skipped={}", handled, sink.parsed(), sink.indexed(), sink.skipped());
        return response;
    }

    /**
     * 读一个文件。
     *
     * @param file      文件
     * @param startLine 从第几行开始读
     * @param maxLines  最多读多少行
     * @param sink      日志接收方
     * @return 读取结果
     */
    private ReadResult readFile(Path file, long startLine, long maxLines, Consumer<LogDocument> sink) {
        LogFileParser parser = new LogFileParser(LogHost.name(), appName);
        long read = 0;
        boolean finished = false;
        try (BufferedReader reader = openReader(file)) {
            for (long index = 0; index < startLine; index++) {
                if (reader.readLine() == null) {
                    break;
                }
            }
            while (read < maxLines) {
                String line = reader.readLine();
                if (line == null) {
                    finished = true;
                    break;
                }
                read++;
                parser.feed(line, sink);
            }
            parser.flush(sink);
        } catch (IOException ex) {
            log.warn("read log file {} failed: {}", file, ex.getMessage());
        }
        return new ReadResult(read, finished);
    }

    /**
     * 判断这个文件是否已经导完。
     *
     * @param state      位点
     * @param size       当前文件大小
     * @param compressed 是否压缩文件
     * @return 已导完返回 true
     */
    private boolean skip(LogImportState state, long size, boolean compressed) {
        if (compressed) {
            return state.isDone();
        }
        // 主日志会一直增长，只有「上次读到末尾」加上「大小没变」才算真的没新东西
        return state.isDone() && state.getSize() == size;
    }

    /**
     * 从哪一行开始读。
     *
     * @param state      位点
     * @param size       当前文件大小
     * @param compressed 是否压缩文件
     * @return 起始行号
     */
    private long startLineOf(LogImportState state, long size, boolean compressed) {
        if (state == null) {
            return 0L;
        }
        // 文件变小只可能是被截断或轮转过，这时行数位点已经失效，从头再来
        if (!compressed && state.getSize() > size) {
            return 0L;
        }
        return state.getLines();
    }

    /**
     * 收集要导的文件。只认主日志与它轮转出来的压缩文件，见类注释。
     *
     * <p>轮转目录名就是轮转日期（logback 的 {@code %d{yyyy-MM-dd}} 目录），
     * 所以整个目录早于保留窗口时可以直接跳过，不必把行预算浪费在读一遍注定要丢的文件上。
     * 不做这一步的话，十几天的历史日志要靠几十次调用才能走到能导的那部分。
     *
     * @param root    日志目录
     * @param cutoff  保留窗口最早的那天
     * @return 文件列表，按路径正序，历史在前
     */
    private List<Path> collectFiles(Path root, LocalDate cutoff) {
        String mainName = appName + ".log";
        Pattern rotated = Pattern.compile(Pattern.quote(appName) + "\\.\\d+\\.log\\.gz");
        try (Stream<Path> stream = Files.walk(root, SCAN_DEPTH)) {
            return stream.filter(Files::isRegularFile).filter(path -> !olderThanWindow(path, root, cutoff)).filter(path -> {
                String name = path.getFileName().toString();
                return name.equals(mainName) || rotated.matcher(name).matches();
            }).sorted().toList();
        } catch (IOException ex) {
            log.warn("scan log dir {} failed: {}", root, ex.getMessage());
            return List.of();
        }
    }

    /**
     * 这个文件所在的轮转目录是否已经整体超出保留窗口。
     *
     * @param path   文件
     * @param root   日志目录
     * @param cutoff 保留窗口最早的那天
     * @return 已超出返回 true
     */
    private boolean olderThanWindow(Path path, Path root, LocalDate cutoff) {
        Path parent = path.getParent();
        if (parent == null || root.equals(parent)) {
            return false;
        }
        String name = parent.getFileName().toString();
        if (!DATE_DIRECTORY.matcher(name).matches()) {
            return false;
        }
        return LocalDate.parse(name).isBefore(cutoff);
    }

    /**
     * 打开文件。压缩文件按 gzip 解压，其余按 UTF-8 文本读。
     *
     * @param file 文件
     * @return 读取器
     * @throws IOException 打开失败
     */
    private BufferedReader openReader(Path file) throws IOException {
        InputStream stream = Files.newInputStream(file);
        if (file.getFileName().toString().endsWith(GZ_SUFFIX)) {
            return new BufferedReader(new InputStreamReader(new GZIPInputStream(stream), StandardCharsets.UTF_8));
        }
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    /**
     * 读取位点。
     *
     * @param file 文件
     * @return 位点，没有返回 null
     */
    private LogImportState loadState(Path file) {
        try {
            var response = elasticsearchClient.get(request -> request.index(naming.metaIndex()).id(file.toString()), LogImportState.class);
            return response.found() ? response.source() : null;
        } catch (Exception ex) {
            // 状态索引还没建时 ES 返回 404，这是第一次导入的正常路径
            return null;
        }
    }

    /**
     * 写入位点。
     *
     * @param state 位点
     */
    private void saveState(LogImportState state) {
        try {
            ensureMetaIndex();
            elasticsearchClient.index(request -> request.index(naming.metaIndex()).id(state.getPath()).document(state));
        } catch (Exception ex) {
            log.warn("save import state for {} failed: {}", state.getPath(), ex.getMessage());
        }
    }

    /**
     * 组装位点。
     *
     * @param file  文件
     * @param lines 已处理行数
     * @param size  文件大小
     * @param done  是否读到末尾
     * @return 位点
     */
    private LogImportState stateOf(Path file, long lines, long size, boolean done) {
        LogImportState state = new LogImportState();
        state.setPath(file.toString());
        state.setLines(lines);
        state.setSize(size);
        state.setDone(done);
        state.setUpdatedAt(Instant.now());
        return state;
    }

    /**
     * 确保状态索引存在。
     *
     * @throws IOException ES 调用失败
     */
    private void ensureMetaIndex() throws IOException {
        if (metaReady) {
            return;
        }
        String index = naming.metaIndex();
        if (!elasticsearchClient.indices().exists(request -> request.index(index)).value()) {
            elasticsearchClient.indices().create(request -> request.index(index).mappings(metaMapping()).settings(metaSettings()));
            log.info("log meta index {} created", index);
        }
        metaReady = true;
    }

    /**
     * 保留窗口最早的那天。含今天，所以保留 N 天时它是 today - (N-1)。
     *
     * @return 保留窗口最早的那天
     */
    private LocalDate cutoffDate() {
        return naming.today().minusDays(Math.max(0, retentionDays - 1));
    }

    /**
     * 文件大小。
     *
     * @param file 文件
     * @return 字节数，取不到返回 0
     */
    private long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException ex) {
            return 0L;
        }
    }

    /**
     * 状态索引映射。显式声明，理由与日志索引一致：不能让动态推断决定字段类型。
     *
     * @return 映射
     */
    private TypeMapping metaMapping() {
        return TypeMapping.of(builder -> builder.properties("path", Property.of(property -> property.keyword(keyword -> keyword))).properties("lines", Property.of(property -> property.long_(number -> number))).properties("size", Property.of(property -> property.long_(number -> number))).properties("done", Property.of(property -> property.boolean_(flag -> flag))).properties("updatedAt", Property.of(property -> property.date(date -> date))));
    }

    /**
     * 状态索引设置。单分片零副本，与日志索引一致。
     *
     * @return 设置
     */
    private IndexSettings metaSettings() {
        return IndexSettings.of(builder -> builder.numberOfShards("1").numberOfReplicas("0"));
    }

    /**
     * 一次文件读取的结果。行数与「是否读到末尾」是两个独立的语义，
     * 揉进一个返回值里迟早会写错——读到最后一行时两者会同时成立。
     *
     * @param lines    实际读到的行数
     * @param finished 是否读到了文件末尾
     */
    private record ReadResult(long lines, boolean finished) {
    }

    /**
     * 回填的日志接收方：攒够一批就按各自的日期归档写入。
     *
     * <p>按日期归档而不是一律写今天是关键。否则「保留 N 天」会把整段历史
     * 一次性判死——刚导进来，下一次清理任务就把它们全删了。
     */
    private static final class ImportSink implements Consumer<LogDocument> {

        /**
         * 日志写入服务。
         */
        private final LogIngestService ingest;

        /**
         * 索引命名。
         */
        private final LogIndexNaming naming;

        /**
         * 保留窗口最早那天的索引名。索引名是定长的 yyyy_MM_dd 结尾，
         * 所以按字典序比较就等于按日期比较。
         */
        private final String cutoffIndex;

        /**
         * 待写入的一批。
         */
        private final List<LogDocument> chunk = new ArrayList<>(BULK_CHUNK);

        /**
         * 已经确认就绪的索引，避免每个 chunk 都去 exists 一次。
         */
        private final Set<String> prepared = new HashSet<>();

        /**
         * 解析出的条数。
         */
        private long parsed;

        /**
         * 写入的条数。
         */
        private long indexed;

        /**
         * 跳过的条数。
         */
        private long skipped;

        ImportSink(LogIngestService ingest, LogIndexNaming naming, String cutoffIndex) {
            this.ingest = ingest;
            this.naming = naming;
            this.cutoffIndex = cutoffIndex;
        }

        @Override
        public void accept(LogDocument document) {
            parsed++;
            String index = naming.indexOf(document.getTimestamp());
            if (index.compareTo(cutoffIndex) < 0) {
                skipped++;
                return;
            }
            chunk.add(document);
            indexed++;
            if (chunk.size() >= BULK_CHUNK) {
                flush();
            }
        }

        /**
         * 把攒下的一批写出去，按日期分组。
         */
        void flush() {
            if (chunk.isEmpty()) {
                return;
            }
            Map<String, List<LogDocument>> byIndex = new LinkedHashMap<>();
            for (LogDocument document : chunk) {
                byIndex.computeIfAbsent(naming.indexOf(document.getTimestamp()), key -> new ArrayList<>()).add(document);
            }
            chunk.clear();
            for (Map.Entry<String, List<LogDocument>> entry : byIndex.entrySet()) {
                if (prepared.add(entry.getKey())) {
                    ingest.ensureIndex(entry.getKey());
                }
                ingest.bulkIndex(entry.getKey(), entry.getValue());
            }
        }

        /**
         * 解析出的条数。
         *
         * @return 条数
         */
        long parsed() {
            return parsed;
        }

        /**
         * 写入的条数。
         *
         * @return 条数
         */
        long indexed() {
            return indexed;
        }

        /**
         * 跳过的条数。
         *
         * @return 条数
         */
        long skipped() {
            return skipped;
        }

    }

}
