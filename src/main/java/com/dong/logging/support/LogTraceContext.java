package com.dong.logging.support;

import org.slf4j.MDC;

/**
 * 链路追踪 id 的存取。
 *
 * <p>key 单独收在这一处是有意的：写入方是 web 层的过滤器，读取方是 logback appender，
 * 两边互不属于对方，各写一个字面量迟早对不上，而对不上的表现是「traceId 永远是空」，
 * 这种问题在运行期极难发现。
 *
 * <p>traceId 是一次请求的唯一标识。日志检索里它是最有用的一个维度：
 * 出问题的时候，先按 traceId 把一次请求的全部日志捞出来，
 * 比在时间窗口里按关键字猜要快得多。
 */
public final class LogTraceContext {

    /**
     * MDC 里的 key。
     */
    public static final String TRACE_ID = "traceId";

    private LogTraceContext() {
    }

    /**
     * 放入当前线程的追踪 id。
     *
     * @param traceId 追踪 id
     */
    public static void put(String traceId) {
        MDC.put(TRACE_ID, traceId);
    }

    /**
     * 清除当前线程的追踪 id。必须在 finally 里调：
     * 线程是复用的，不清会在下一次请求里看到上一个人的 traceId。
     *
     * <p>而这恰恰是最危险的一类错误：数据没丢、也没报错，
     * 只是两条不同请求的日志被串成了一条。
     */
    public static void clear() {
        MDC.remove(TRACE_ID);
    }

    /**
     * 当前线程的追踪 id。
     *
     * @return 追踪 id，没有返回 null
     */
    public static String current() {
        return MDC.get(TRACE_ID);
    }

}
