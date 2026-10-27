package com.dong.logging.service;

import com.dong.logging.dto.LogContextResponse;
import com.dong.logging.dto.LogOverviewResponse;
import com.dong.logging.dto.LogSearchRequest;
import com.dong.logging.dto.LogSearchResponse;

/**
 * 日志检索。
 *
 * <p>检索与仪表盘共用一套过滤条件（LogSearchRequest），
 * 保证「图上看到的数字」与「列表里查到的条数」永远一致。
 *
 * <p>默认按时间倒序：日志检索几乎都是「看刚刚发生了什么」，
 * 相关性排序在这里没有意义。
 */
public interface LogSearchService {

    /**
     * 分页检索。受 ES 一万条结果窗口限制，翻更深要收窄时间范围。
     *
     * @param request 检索条件
     * @return 检索结果
     */
    LogSearchResponse search(LogSearchRequest request);

    /**
     * 仪表盘聚合。一次返回级别分布、时间直方图、Top logger 与 Top 异常类型。
     *
     * @param request 过滤条件
     * @return 仪表盘数据
     */
    LogOverviewResponse overview(LogSearchRequest request);

    /**
     * 上下文查询。取目标日志前后各若干条，用来还原它发生时周围发生了什么。
     *
     * @param id     目标日志的文档 id
     * @param before 往前取多少条
     * @param after  往后取多少条
     * @return 上下文
     */
    LogContextResponse context(String id, int before, int after);

}
