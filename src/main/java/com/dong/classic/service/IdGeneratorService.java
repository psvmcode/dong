package com.dong.classic.service;

import java.util.Map;

/**
 * 发号器。四种策略各有取舍：
 * 雪花算法趋势递增但依赖机器时钟，时钟回拨会产生重复；
 * 号段模式对数据库有压力但绝对递增；
 * INCR 最简单但会暴露业务量且需持久化；
 * UUID 无序，做数据库主键会导致页分裂。
 */
public interface IdGeneratorService {

    /**
     * 按策略批量生成 id 并统计耗时与重复情况。
     */
    Map<String, Object> generate(String strategy, int count);

}
