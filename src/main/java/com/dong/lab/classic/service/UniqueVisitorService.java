package com.dong.lab.classic.service;

import java.time.LocalDate;

/**
 * 独立访客统计。基于 HyperLogLog，每个页面每天固定占用约 12KB，
 * 与访问量无关，代价是结果有约百分之零点八的误差。
 * 金额这类需要精确去重的场景不能用它。
 */
public interface UniqueVisitorService {

    /**
     * 记录一次访问，返回当前估算值。
     */
    long record(String page, String visitorId, LocalDate date);

    /**
     * 查询指定日期的估算值。
     */
    long count(String page, LocalDate date);

    /**
     * 查询日期区间的估算值，合并多个 HyperLogLog 去重而非简单相加。
     */
    long countBetween(String page, LocalDate from, LocalDate to);

}
