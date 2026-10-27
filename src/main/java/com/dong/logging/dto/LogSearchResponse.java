package com.dong.logging.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 日志检索结果。默认按时间倒序：日志检索绝大多数时候是「看刚刚发生了什么」，
 * 相关性排序在这里没有意义——一条 ERROR 不会因为出现了两次就更值得看。
 */
@Data
public class LogSearchResponse {

    /**
     * 命中总数。
     */
    private long total;

    /**
     * 当前页码。
     */
    private int pageNum;

    /**
     * 每页条数。
     */
    private int pageSize;

    /**
     * 当前页的日志。
     */
    private List<Hit> list;

    /**
     * 一条日志。
     */
    @Data
    public static class Hit {

        /**
         * 文档 id。上下文查询靠它定位，ES 自动生成的，不要当成业务主键。
         */
        private String id;

        /**
         * 日志时间。
         */
        private Instant timestamp;

        /**
         * 级别。
         */
        private String level;

        /**
         * logger 名。
         */
        private String logger;

        /**
         * 线程名。
         */
        private String thread;

        /**
         * 日志正文。
         */
        private String message;

        /**
         * 异常类型，没有异常时为空。
         */
        private String exceptionType;

        /**
         * 异常堆栈，没有异常时为空。
         */
        private String exception;

        /**
         * 链路追踪 id，非请求线程里为空。
         */
        private String traceId;

        /**
         * 主机名。
         */
        private String host;

        /**
         * 正文的高亮片段。命中多个片段时 ES 返回多段。
         */
        private List<String> messageHighlights;

        /**
         * 堆栈的高亮片段。
         */
        private List<String> exceptionHighlights;

    }

}
