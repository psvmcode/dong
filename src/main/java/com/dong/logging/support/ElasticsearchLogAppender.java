package com.dong.logging.support;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.dong.common.constant.Constants;
import com.dong.logging.entity.LogDocument;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 日志入 ES 的第一跳：把事件转成文档、塞进有界缓冲，仅此而已。
 *
 * <p>用 UnsynchronizedAppenderBase 而不是 AppenderBase：后者在 doAppend 上加了
 * synchronized，所有线程打日志都要串行过这把锁。这里的共享状态只有一个
 * 线程安全的队列，不需要它来兜底，同步纯属白付代价。
 *
 * <p>方法体整体包在 try/catch 里是硬要求：appender 抛异常会顺着
 * {@code log.xyz()} 一路抛回业务代码，一个日志组件把业务流程打断是不可接受的。
 *
 * <p>环路过滤也是硬要求。ES 客户端、HTTP 组件、以及本模块自己都会打日志，
 * 它们一旦入队就会被下一次刷写再触发一次日志，形成自激。
 * 这里按 logger 前缀直接挡掉，代价是这几个包的日志不进 ES——
 * 它们本来就是排查 ES 连通性时才看的，那种场景直接看落盘文件更可靠。
 */
public class ElasticsearchLogAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    /**
     * 默认队列容量，可被 logback 配置里的 queueSize 覆盖。
     */
    private static final int DEFAULT_QUEUE_SIZE = 10_000;

    /**
     * 默认应用名，可被 appName 覆盖。
     */
    private static final String DEFAULT_APP_NAME = "dong";

    /**
     * 正文截断长度。一条日志带着几 MB 参数 JSON 打出来是常事，
     * 不截断会让单个文档把 ES 的写入与检索一起拖慢。
     */
    private static final int MAX_MESSAGE_LENGTH = Constants.MAX_TEXT_LENGTH;

    /**
     * 堆栈截断长度，比正文宽一些：堆栈是排查问题时唯一不能缺的部分。
     */
    private static final int MAX_STACK_LENGTH = 2 * Constants.MAX_TEXT_LENGTH;

    /**
     * 只打前几次异常，避免日志组件自身的故障把日志刷爆。
     */
    private static final int MAX_WARN_TIMES = 3;

    /**
     * 不进 ES 的 logger 前缀，见类注释里的环路说明。
     */
    private static final List<String> EXCLUDED_LOGGER_PREFIXES = List.of("com.dong.logging", "org.elasticsearch", "co.elastic.clients", "org.apache.http");

    /**
     * 已报告的自身故障次数。
     */
    private static final AtomicLong WARN_COUNT = new AtomicLong();

    /**
     * 队列容量。
     */
    private int queueSize = DEFAULT_QUEUE_SIZE;

    /**
     * 应用名。
     */
    private String appName = DEFAULT_APP_NAME;

    /**
     * 设置队列容量，由 logback 配置注入。
     *
     * @param queueSize 队列容量
     */
    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    /**
     * 设置应用名，由 logback 配置注入。
     *
     * @param appName 应用名
     */
    public void setAppName(String appName) {
        this.appName = appName;
    }

    /**
     * 启动时把容量交给缓冲。这一步必须在第一次入队之前完成——
     * 队列建成之后容量就改不了了。
     */
    @Override
    public void start() {
        LogEventBuffer.configure(queueSize);
        super.start();
    }

    /**
     * 处理一条日志事件。
     *
     * @param event 日志事件
     */
    @Override
    protected void append(ILoggingEvent event) {
        try {
            if (isExcluded(event.getLoggerName())) {
                return;
            }
            LogEventBuffer.instance().offer(toDocument(event));
        } catch (RuntimeException ex) {
            if (WARN_COUNT.incrementAndGet() <= MAX_WARN_TIMES) {
                addWarn("failed to buffer log event: " + ex.getMessage());
            }
        }
    }

    /**
     * 转成索引文档。
     *
     * @param event 日志事件
     * @return 日志文档
     */
    private LogDocument toDocument(ILoggingEvent event) {
        LogDocument document = new LogDocument();
        document.setTimestamp(Instant.ofEpochMilli(event.getTimeStamp()));
        document.setLevel(event.getLevel() == null ? "INFO" : event.getLevel().toString());
        document.setLogger(event.getLoggerName());
        document.setThread(event.getThreadName());
        document.setMessage(truncate(event.getFormattedMessage(), MAX_MESSAGE_LENGTH));
        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable != null) {
            document.setExceptionType(throwable.getClassName());
            document.setException(truncate(ThrowableProxyUtil.asString(throwable), MAX_STACK_LENGTH));
        }
        document.setTraceId(traceIdOf(event));
        document.setHost(LogHost.name());
        document.setApp(appName);
        return document;
    }

    /**
     * 取 MDC 里的追踪 id。
     *
     * @param event 日志事件
     * @return 追踪 id，没有返回 null
     */
    private String traceIdOf(ILoggingEvent event) {
        Map<String, String> mdc = event.getMDCPropertyMap();
        if (mdc == null) {
            return null;
        }
        return mdc.get(LogTraceContext.TRACE_ID);
    }

    /**
     * 是否是不进 ES 的 logger。
     *
     * @param loggerName logger 名
     * @return 需要排除返回 true
     */
    private boolean isExcluded(String loggerName) {
        if (loggerName == null) {
            return true;
        }
        for (String prefix : EXCLUDED_LOGGER_PREFIXES) {
            if (loggerName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 截断超长文本。保留尾部而不是头部：日志的关键信息（异常原因、结果）通常在末尾。
     *
     * @param text 原文
     * @param max  最大长度
     * @return 截断后的文本
     */
    private String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...(truncated " + (text.length() - max) + " chars)";
    }

}
