package com.dong.lab.classic.service;

import com.dong.lab.classic.dto.RankItemResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * 排行榜。基于 Redis ZSet，写入与查询均为对数复杂度。
 */
public interface LeaderboardService {

    /**
     * 设置分数，已存在则覆盖。
     */
    void submit(String board, String member, double score);

    /**
     * 在原分数上累加，返回累加后的值。
     */
    Double addScore(String board, String member, double delta);

    /**
     * 取前 N 名，分数相同时按成员字典序。
     */
    List<RankItemResponse> top(String board, int size);

    /**
     * 查询名次，从 0 开始。
     */
    Long rankOf(String board, String member);

    /**
     * 查询分数，成员不存在返回 null。
     */
    Double scoreOf(String board, String member);

    /**
     * 查询某成员前后各若干名。
     */
    List<RankItemResponse> around(String board, String member, int range);

    /**
     * 查询榜单总人数。
     */
    Long size(String board);

    /**
     * 移除某个成员。
     */
    void remove(String board, String member);

    /**
     * 结算周榜，把当周数据固化为历史记录并清空当前榜。
     */
    Long settleWeekly(String board, LocalDate date);

    /**
     * 清空整个榜单。
     */
    void clear(String board);

}
