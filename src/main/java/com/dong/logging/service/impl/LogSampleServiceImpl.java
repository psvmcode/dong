package com.dong.logging.service.impl;

import com.dong.logging.service.LogIngestService;
import com.dong.logging.service.LogSampleService;
import com.dong.logging.support.LogTraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 样本日志生成实现。
 *
 * <p>生成的日志走完整链路：真的经过 logback、真的进缓冲、真的由刷写任务写进 ES，
 * 而不是直接调 bulk 造数据。否则验证的是写入接口，不是采集管道——
 * 而采集管道恰恰是最容易出问题的一环（缓冲、降级、刷写节奏都在这里）。
 *
 * <p>logger 名借用各模块的真实类名，这样仪表盘里的 Top logger 才有看头：
 * 一批日志全叫同一个名字，等于把「哪个模块在刷日志」这个视角直接废掉。
 *
 * <p>最后会把缓冲排空并强制刷新。不这么做的话，刚生成的日志要等下一个刷写周期
 * 加上索引的 refresh_interval 才可见，页面上会表现为「点了没反应」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"dong.logging.enabled", "dong.elasticsearch.enabled"}, havingValue = "true")
public class LogSampleServiceImpl implements LogSampleService {

    /**
     * 借用的模块 logger。
     */
    private static final List<String> LOGGERS = List.of("com.dong.cache.service.impl.CacheServiceImpl", "com.dong.search.service.impl.SearchServiceImpl", "com.dong.seckill.service.impl.SeckillServiceImpl", "com.dong.redpacket.service.impl.RedPacketServiceImpl", "com.dong.order.service.impl.OrderServiceImpl", "com.dong.upload.service.impl.UploadServiceImpl", "com.dong.crossborder.service.impl.RemittanceServiceImpl");

    /**
     * 正文模板。
     */
    private static final List<String> ACTIONS = List.of("cache miss, loading from database", "cache hit, returning cached value", "order created, waiting for payment", "seckill stock deducted", "red packet item grabbed", "product document indexed", "upload chunk received", "remittance submitted for compliance check", "redis command timed out, falling back", "database connection pool exhausted");

    /**
     * 多少条共用同一个追踪 id。
     */
    private static final int TRACE_GROUP_SIZE = 4;

    /**
     * ingestService，日志写入服务。
     */
    private final LogIngestService ingestService;

    /**
     * 生成样本日志。
     */
    @Override
    public int generate(int count, boolean withException, boolean withTrace) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        try {
            for (int index = 0; index < count; index++) {
                if (withTrace && index % TRACE_GROUP_SIZE == 0) {
                    LogTraceContext.put(newTraceId());
                }
                emit(random, withException);
            }
        } finally {
            // 跑在请求线程上，MDC 必须清掉，否则下一个请求会带着上一个的追踪 id
            LogTraceContext.clear();
        }
        try {
            ingestService.flush(Integer.MAX_VALUE);
            ingestService.refresh();
        } catch (Exception ex) {
            log.warn("flush generated logs failed: {}", ex.getMessage());
        }
        return count;
    }

    /**
     * 打一条日志。级别按权重随机：日志里 INFO 占大多数是常态，
     * 全打成 ERROR 会让仪表盘上的错误率失去参考价值。
     *
     * @param random        随机数
     * @param withException 错误是否带堆栈
     */
    private void emit(ThreadLocalRandom random, boolean withException) {
        Logger logger = LoggerFactory.getLogger(LOGGERS.get(random.nextInt(LOGGERS.size())));
        String message = ACTIONS.get(random.nextInt(ACTIONS.size())) + " id=" + random.nextLong(1_000_000L) + " cost=" + random.nextInt(200) + "ms";
        int roll = random.nextInt(100);
        if (roll < 45) {
            logger.info(message);
            return;
        }
        if (roll < 70) {
            logger.debug(message + " detail=trace-sampled");
            return;
        }
        if (roll < 90) {
            logger.warn(message + " retry=" + random.nextInt(3));
            return;
        }
        if (withException) {
            logger.error(message + " failed", new IllegalStateException("simulated failure: " + message));
            return;
        }
        logger.error(message + " failed");
    }

    /**
     * 生成追踪 id。与全局异常处理器里的做法保持一致：12 位十六进制，
     * 短到能口头报出来，长到不会撞。
     *
     * @return 追踪 id
     */
    private String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

}
