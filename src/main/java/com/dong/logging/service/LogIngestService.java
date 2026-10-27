package com.dong.logging.service;

import com.dong.logging.dto.LogPipelineStats;
import com.dong.logging.entity.LogDocument;

import java.util.List;

/**
 * 日志采集与写入。
 *
 * <p>这一层只管「把日志写进 ES」，不管日志从哪来：
 * 实时日志由 appender 进缓冲后从这里刷出去，历史日志由回填服务直接调 bulkIndex。
 *
 * <p>写入一律走 bulk：一条一次请求在日志这种量级下会把连接数和网络往返同时打满。
 * bulk 接口即使单条失败也返回 200，所以每个 bulk 调用都必须检查 errors 标志。
 */
public interface LogIngestService {

    /**
     * 确保今天要写的索引就绪。跨天时自动切到新索引并挂上读别名。
     *
     * <p>失败会退避：ES 不可用时不能每秒都去重试建索引，
     * 那会把一次中间件故障放大成持续的错误风暴。
     *
     * @return 当前写入的索引名
     */
    String ensureWriteIndex();

    /**
     * 确保指定索引就绪，用于把历史日志写回它自己那一天的索引。
     *
     * @param index 索引名
     * @return 索引名
     */
    String ensureIndex(String index);

    /**
     * 把缓冲里的日志刷进 ES。
     *
     * @param maxBatch 本次最多刷多少条
     * @return 实际写入的条数
     */
    int flush(int maxBatch);

    /**
     * 批量写入指定索引。
     *
     * @param index     索引名
     * @param documents 日志文档
     */
    void bulkIndex(String index, List<LogDocument> documents);

    /**
     * 刷新读别名，让刚写入的日志立刻可查。
     *
     * <p>只在批量导入、生成样本这类一次性动作后调用：索引的 refresh_interval 是 5 秒，
     * 常态下靠它自动刷新就够，每次写入都强制刷新会把 ES 的段合并压垮。
     */
    void refresh();

    /**
     * 删除超出保留天数的索引，天数取配置的 dong.logging.retention-days。
     *
     * @return 删除的索引数
     */
    int cleanupExpired();

    /**
     * 采集管道的运行状态。
     *
     * @return 管道状态
     */
    LogPipelineStats stats();

    /**
     * 读别名背后的全部索引。
     *
     * @return 索引名列表，按名字正序
     */
    List<String> listIndices();

}
