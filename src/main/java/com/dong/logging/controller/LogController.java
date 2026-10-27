package com.dong.logging.controller;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.common.result.Result;
import com.dong.logging.dto.LogContextRequest;
import com.dong.logging.dto.LogContextResponse;
import com.dong.logging.dto.LogGenerateRequest;
import com.dong.logging.dto.LogImportRequest;
import com.dong.logging.dto.LogImportResponse;
import com.dong.logging.dto.LogOverviewResponse;
import com.dong.logging.dto.LogPipelineStats;
import com.dong.logging.dto.LogSearchRequest;
import com.dong.logging.dto.LogSearchResponse;
import com.dong.logging.service.LogFileImportService;
import com.dong.logging.service.LogIngestService;
import com.dong.logging.service.LogSampleService;
import com.dong.logging.service.LogSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 日志检索。把 ELK 的三件事合成一个模块：
 *
 * <ul>
 *   <li><b>采集（对应 Logstash / Filebeat）</b>：logback appender 把事件转文档进有界缓冲，
 *       定时任务批量刷进 ES；落盘的历史日志另有回填接口按位点续传。</li>
 *   <li><b>存储（对应 Elasticsearch）</b>：按天切索引 + 读别名，
 *       过期按整块索引删除，保留天数与落盘文件一致。</li>
 *   <li><b>检索与可视化（对应 Kibana）</b>：检索、上下文、仪表盘聚合，
 *       外加一组管道状态接口——日志管道出问题时不会报错，只会安静地少数据，
 *       没有这组数字就只能靠猜。</li>
 * </ul>
 *
 * <p>接口按 dong.logging.enabled 与 dong.elasticsearch.enabled 两个开关启停，
 * 没开时返回中间件未启用，而不是把 ES 的错误直接抛给调用方。
 */
@RestController
@Validated
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Tag(name = "日志检索")
public class LogController {

    /**
     * logSearchServiceProvider，日志检索服务提供者。
     */
    private final ObjectProvider<LogSearchService> logSearchServiceProvider;

    /**
     * logIngestServiceProvider，日志写入服务提供者。
     */
    private final ObjectProvider<LogIngestService> logIngestServiceProvider;

    /**
     * logFileImportServiceProvider，历史日志回填服务提供者。
     */
    private final ObjectProvider<LogFileImportService> logFileImportServiceProvider;

    /**
     * logSampleServiceProvider，样本日志服务提供者。
     */
    private final ObjectProvider<LogSampleService> logSampleServiceProvider;

    /**
     * 日志检索。关键字进 must 参与打分，级别、logger 前缀、追踪 id、时间范围进 filter 只做筛选，
     * 结果带正文与堆栈两处高亮。默认按时间倒序。
     */
    @PostMapping("/search")
    @Operation(summary = "日志检索：关键字、级别、logger 前缀、追踪 id、时间范围，按时间倒序")
    public Result<LogSearchResponse> search(@Valid @RequestBody LogSearchRequest request) {
        return Result.success(requireSearchService().search(request));
    }

    /**
     * 仪表盘。与检索共用同一套过滤条件，图上看到的数字与列表里查到的条数永远一致。
     */
    @PostMapping("/overview")
    @Operation(summary = "仪表盘：级别分布、时间直方图、Top logger、Top 异常类型")
    public Result<LogOverviewResponse> overview(@Valid @RequestBody LogSearchRequest request) {
        return Result.success(requireSearchService().overview(request));
    }

    /**
     * 上下文。一条 ERROR 本身常常说明不了问题，真正的原因在它前面几行。
     */
    @PostMapping("/context")
    @Operation(summary = "上下文：取某条日志前后各若干条，还原它发生时周围发生了什么")
    public Result<LogContextResponse> context(@Valid @RequestBody LogContextRequest request) {
        return Result.success(requireSearchService().context(request.getId(), request.getBefore(), request.getAfter()));
    }

    /**
     * 管道状态。缓冲积压与丢弃条数是判断「日志到底采没采到」的第一手依据。
     */
    @PostMapping("/stats")
    @Operation(summary = "采集管道状态：缓冲积压、丢弃条数、写入条数、索引清单")
    public Result<LogPipelineStats> stats() {
        return Result.success(requireIngestService().stats());
    }

    /**
     * 生成样本日志。页面在没有真实流量时是空的，先把数据造出来才能验证检索与图表。
     */
    @PostMapping("/generate")
    @Operation(summary = "生成样本日志：走完整采集链路，用于验证检索与可视化")
    public Result<Integer> generate(@Valid @RequestBody LogGenerateRequest request) {
        return Result.success(requireSampleService().generate(request.getCount(), request.isWithException(), request.isWithTrace()));
    }

    /**
     * 回填历史日志。按位点续传，多次调用直到补完。
     */
    @PostMapping("/import")
    @Operation(summary = "从落盘日志文件回填历史日志，按行数位点续传")
    public Result<LogImportResponse> importFiles(@Valid @RequestBody LogImportRequest request) {
        return Result.success(requireImportService().importFiles(request.getMaxLines()));
    }

    /**
     * 清理过期索引。保留天数取配置，与落盘文件的保留天数一致。
     */
    @PostMapping("/retention")
    @Operation(summary = "清理超出保留天数的日志索引，按天整块删除")
    public Result<Integer> retention() {
        return Result.success(requireIngestService().cleanupExpired());
    }

    /**
     * 取日志检索服务。
     *
     * @return 日志检索服务
     */
    private LogSearchService requireSearchService() {
        LogSearchService service = logSearchServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BusinessException(Constants.CODE_MIDDLEWARE_DISABLED, "set dong.logging.enabled=true and dong.elasticsearch.enabled=true first");
        }
        return service;
    }

    /**
     * 取日志写入服务。
     *
     * @return 日志写入服务
     */
    private LogIngestService requireIngestService() {
        LogIngestService service = logIngestServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BusinessException(Constants.CODE_MIDDLEWARE_DISABLED, "set dong.logging.enabled=true and dong.elasticsearch.enabled=true first");
        }
        return service;
    }

    /**
     * 取历史日志回填服务。
     *
     * @return 历史日志回填服务
     */
    private LogFileImportService requireImportService() {
        LogFileImportService service = logFileImportServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BusinessException(Constants.CODE_MIDDLEWARE_DISABLED, "set dong.logging.enabled=true and dong.elasticsearch.enabled=true first");
        }
        return service;
    }

    /**
     * 取样本日志服务。
     *
     * @return 样本日志服务
     */
    private LogSampleService requireSampleService() {
        LogSampleService service = logSampleServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BusinessException(Constants.CODE_MIDDLEWARE_DISABLED, "set dong.logging.enabled=true and dong.elasticsearch.enabled=true first");
        }
        return service;
    }

}
