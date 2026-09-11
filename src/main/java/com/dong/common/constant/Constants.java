package com.dong.common.constant;

public final class Constants {

    public static final int CODE_SUCCESS = 0;

    public static final int CODE_PARAM_INVALID = 1000;

    public static final int CODE_DATA_NOT_FOUND = 1001;

    public static final int CODE_OPERATION_CONFLICT = 1002;

    public static final int CODE_TOO_MANY_REQUESTS = 1003;

    public static final int CODE_MIDDLEWARE_DISABLED = 1004;

    public static final int CODE_DEPENDENCY_UNAVAILABLE = 1005;

    public static final int CODE_IDEMPOTENT_REJECTED = 1006;

    public static final int CODE_INTERNAL_ERROR = 5000;

    public static final String MESSAGE_SUCCESS = "success";

    public static final String MESSAGE_PARAM_INVALID = "invalid parameter";

    public static final String MESSAGE_DATA_NOT_FOUND = "data not found";

    public static final String MESSAGE_OPERATION_CONFLICT = "operation conflict, please retry";

    public static final String MESSAGE_TOO_MANY_REQUESTS = "too many requests";

    public static final String MESSAGE_MIDDLEWARE_DISABLED = "middleware is disabled, turn it on in application.yml";

    public static final String MESSAGE_DEPENDENCY_UNAVAILABLE = "downstream dependency is unavailable";

    public static final String MESSAGE_IDEMPOTENT_REJECTED = "duplicate request rejected";

    public static final String MESSAGE_INTERNAL_ERROR = "internal server error";

    public static final String REDIS_PREFIX = "lab";

    public static final String SEPARATOR = ":";

    public static final int DEFAULT_PAGE_NUM = 1;

    public static final int DEFAULT_PAGE_SIZE = 20;

    public static final int MAX_PAGE_SIZE = 200;

    /**
     * 页码上限。偏移量由 (pageNum - 1) * pageSize 算出，
     * 不限制页码会让乘法溢出成负数，直接把 limit 语句变成语法错误。
     */
    public static final int MAX_PAGE_NUM = 1_000_000;

    /**
     * 通用批量条数上限。凡是「一次处理 N 条」的参数都用它封顶，
     * 不封顶的话传一个极大值就能把内存吃光。
     */
    public static final int MAX_BATCH_SIZE = 1000;

    /**
     * 并发线程数上限。压测类接口靠它兜底，
     * 否则传一个天文数字会瞬间创建海量任务。
     */
    public static final int MAX_THREADS = 200;

    /**
     * 单线程循环次数上限。与线程数相乘才是实际任务量，
     * 两个参数都必须限制，只限制一个等于没限制。
     */
    public static final int MAX_LOOPS = 500;

    /**
     * 通用查询条数上限，比批量上限更严，因为查询要组装返回对象。
     */
    public static final int MAX_QUERY_LIMIT = 200;

    /**
     * 普通字符串长度上限，用于名称、编码、标识等短字段。
     */
    public static final int MAX_NAME_LENGTH = 128;

    /**
     * 长文本长度上限，用于描述、备注、消息体。
     */
    public static final int MAX_TEXT_LENGTH = 4096;

    /**
     * 延迟时长上限，单位秒，即 24 小时。
     * 不限制的话传 Long.MAX_VALUE 会让任务永远不触发且无法回收。
     */
    public static final long MAX_DELAY_SECONDS = 86_400L;

    /**
     * 时间窗口上限，单位秒，即 24 小时。
     */
    public static final long MAX_WINDOW_SECONDS = 86_400L;

    private Constants() {
    }

}
