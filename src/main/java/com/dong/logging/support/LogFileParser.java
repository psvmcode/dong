package com.dong.logging.support;

import com.dong.common.constant.Constants;
import com.dong.logging.entity.LogDocument;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 历史日志文件的逐行解析器，对应 Filebeat 的「读文件 + 拼多行」这一环。
 *
 * <p>解析的是 logback 落盘文件的格式：
 * {@code 2026-10-27T00:12:50.862+08:00 ERROR 40593 --- [dong] [redisson-netty-4-17] o.r.c.h.ErrorsLoggingHandler : xxx}。
 * 两种行：命中时间戳开头的算新的一条，其余全是上一条的续行（异常堆栈）。
 * 不做这个区分的话，堆栈每一行都会变成一条独立日志，检索结果直接没法看。
 *
 * <p>方括号要取「最后一组」：应用名 [dong] 与线程名 [xxx] 都带括号，
 * 而且只取冒号之前的部分——日志正文里出现 ] 是常事，在整行里找最后一个
 * 右括号一定会找错。
 *
 * <p>解析器只认格式，不碰 IO，也不管位点：读文件与断点续传在
 * {@code LogFileImportServiceImpl} 里，两边分开才好单独验证。
 */
public final class LogFileParser {

    /**
     * 日志头。时间戳 + 级别 + 进程号 + --- + 应用名与线程名方括号 + logger + 正文。
     */
    private static final Pattern HEADER = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}(?:[+-]\\d{2}:\\d{2}|Z))\\s+([A-Z]+)\\s+\\S*\\s*---\\s+(.*)$");

    /**
     * 异常类型。容忍 Caused by 前缀，也容忍行尾的原因描述。
     */
    private static final Pattern EXCEPTION_TYPE = Pattern.compile("^(?:Caused by:\\s*)?([\\w$.]*(?:Exception|Error|Throwable))(?::.*)?$");

    /**
     * 正文截断长度。与 appender 保持一致，避免同一条日志两个来源长得不一样。
     */
    private static final int MAX_MESSAGE_LENGTH = Constants.MAX_TEXT_LENGTH;

    /**
     * 堆栈截断长度。
     */
    private static final int MAX_STACK_LENGTH = 2 * Constants.MAX_TEXT_LENGTH;

    /**
     * 主机名。
     */
    private final String host;

    /**
     * 应用名。
     */
    private final String app;

    /**
     * 尚未吐出的那条日志。
     */
    private LogDocument pending;

    /**
     * 尚未吐出那条日志的堆栈。
     */
    private final StringBuilder stack = new StringBuilder();

    /**
     * 已识别到的异常类型。
     */
    private String exceptionType;

    /**
     * 用主机名与应用名构造解析器。历史日志里没有这两个信息，只能由导入方补。
     *
     * @param host 主机名
     * @param app  应用名
     */
    public LogFileParser(String host, String app) {
        this.host = host;
        this.app = app;
    }

    /**
     * 喂入一行。命中新日志头时，上一条（含它的堆栈）先通过 sink 吐出。
     *
     * @param line 文件中的一行
     * @param sink 完整日志的接收方
     */
    public void feed(String line, Consumer<LogDocument> sink) {
        Matcher matcher = HEADER.matcher(line);
        if (matcher.matches()) {
            emit(sink);
            pending = newDocument(matcher);
            return;
        }
        if (pending == null) {
            return;
        }
        String text = line.strip();
        if (text.isEmpty()) {
            return;
        }
        if (exceptionType == null) {
            Matcher type = EXCEPTION_TYPE.matcher(text);
            if (type.matches()) {
                exceptionType = type.group(1);
            }
        }
        if (stack.length() < MAX_STACK_LENGTH) {
            stack.append(text).append('\n');
        }
    }

    /**
     * 收尾，把最后一条吐出来。
     *
     * @param sink 完整日志的接收方
     */
    public void flush(Consumer<LogDocument> sink) {
        emit(sink);
    }

    /**
     * 吐出当前这条。
     *
     * @param sink 接收方
     */
    private void emit(Consumer<LogDocument> sink) {
        LogDocument document = pending;
        pending = null;
        exceptionType = null;
        if (document == null) {
            stack.setLength(0);
            return;
        }
        if (stack.length() > 0) {
            document.setException(stack.toString().strip());
            document.setExceptionType(exceptionType);
        }
        stack.setLength(0);
        sink.accept(document);
    }

    /**
     * 按日志头构造文档。时间戳解析不出来就返回 null，
     * 这条以及它后面的续行一起放弃——时间错了的日志进了索引只会污染时间直方图。
     *
     * @param matcher 日志头的匹配结果
     * @return 日志文档，解析失败返回 null
     */
    private LogDocument newDocument(Matcher matcher) {
        Instant timestamp = parseTime(matcher.group(1));
        if (timestamp == null) {
            return null;
        }
        String rest = matcher.group(3);
        int separator = rest.indexOf(" : ");
        String head = rest;
        String message = "";
        if (separator >= 0) {
            head = rest.substring(0, separator);
            message = rest.substring(separator + 3);
        } else {
            int colon = rest.indexOf(": ");
            if (colon >= 0) {
                head = rest.substring(0, colon);
                message = rest.substring(colon + 2);
            }
        }
        String thread = null;
        String logger = head.trim();
        int close = head.lastIndexOf(']');
        if (close >= 0) {
            int open = head.lastIndexOf('[', close);
            if (open >= 0) {
                thread = head.substring(open + 1, close).trim();
                logger = head.substring(close + 1).trim();
            }
        }
        LogDocument document = new LogDocument();
        document.setTimestamp(timestamp);
        document.setLevel(matcher.group(2));
        document.setLogger(logger);
        document.setThread(thread);
        document.setMessage(truncate(message, MAX_MESSAGE_LENGTH));
        document.setHost(host);
        document.setApp(app);
        return document;
    }

    /**
     * 解析 ISO 偏移时间。
     *
     * @param text 时间文本
     * @return 时刻，解析失败返回 null
     */
    private Instant parseTime(String text) {
        try {
            return OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * 截断超长文本。
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
