package com.dong.logging.entity;

import lombok.Data;

import java.time.Instant;

/**
 * 索引里的日志文档。
 *
 * <p>字段分两类：检索型（message、exception 走分词，参与打分与高亮）
 * 和过滤型（level、logger、thread、traceId、host 全是 keyword，只做精确筛选与分桶）。
 * 混在一起是日志检索最常见的坑——把 logger 建成 text，
 * 「按 logger 分桶」会直接报错，因为 text 字段没有 doc_values。
 *
 * <p>message 用 IK 分词：日志正文里中英文混排是常态，
 * 标准分词器会把整句中文当成一个词，只能全等匹配。
 *
 * <p>时间戳用 Instant 而不是字符串：排序与范围过滤都在 ES 侧按 date 类型做，
 * 转成字符串再比较会变成字典序，跨天就错。
 */
@Data
public class LogDocument {

    /**
     * 日志时间。
     */
    private Instant timestamp;

    /**
     * 级别，如 INFO / WARN / ERROR。
     */
    private String level;

    /**
     * logger 名，通常是类全名。
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
     * 异常类型名，没有异常时为空。
     */
    private String exceptionType;

    /**
     * 异常堆栈。单独成字段而不是拼进 message，
     * 否则正文检索会被几十行 at 开头的堆栈污染。
     */
    private String exception;

    /**
     * 链路追踪 id，取自 MDC。非请求线程（定时任务、消息消费）里没有值。
     */
    private String traceId;

    /**
     * 主机名。多实例部署时靠它区分来源。
     */
    private String host;

    /**
     * 应用名。
     */
    private String app;

}
