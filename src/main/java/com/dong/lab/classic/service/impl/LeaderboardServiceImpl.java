package com.dong.lab.classic.service.impl;

import com.dong.lab.classic.dto.RankItemResponse;
import com.dong.lab.classic.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.protocol.ScoredEntry;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
/**
 * 排行榜实现。基于 Redis ZSet，写入与查询均为对数复杂度，
 * 相比数据库排序分页，在高频更新场景下代价低得多。
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class LeaderboardServiceImpl implements LeaderboardService {

    private static final String BOARD = "lab:rank:";

    private static final String WEEKLY = "lab:rank:weekly:";

    private static final String HISTORY = "lab:rank:history:";

    /**
     * Redisson 客户端。
     */
    private final RedissonClient redissonClient;

    /**
     * 设置成员分数，已存在则覆盖。
     *
     * @param board  榜单标识
     * @param member 成员标识
     * @param score  分数
     */
    @Override
    public void submit(String board, String member, double score) {
        boardSet(board).add(score, member);
        log.info("leaderboard submit board={} member={} score={}", board, member, score);
    }

    /**
     * 累加成员分数并返回累加后的值。
     *
     * @param board  榜单标识
     * @param member 成员标识
     * @param delta  增量
     * @return 累加后的分数
     */
    @Override
    public Double addScore(String board, String member, double delta) {
        return boardSet(board).addScore(member, delta);
    }

    /**
     * 查询榜单前 N 名。
     *
     * @param board 榜单标识
     * @param size  前 N 名
     * @return 排行榜列表
     */
    @Override
    public List<RankItemResponse> top(String board, int size) {
        Collection<ScoredEntry<String>> entries = boardSet(board).entryRangeReversed(0, size - 1);
        return toItems(entries, 1L);
    }

    /**
     * 查询成员名次，从 0 开始。
     *
     * @param board  榜单标识
     * @param member 成员标识
     * @return 名次
     */
    @Override
    public Long rankOf(String board, String member) {
        Integer rank = boardSet(board).revRank(member);
        return rank == null ? null : rank.longValue();
    }

    /**
     * 查询成员分数，不存在返回 null。
     *
     * @param board  榜单标识
     * @param member 成员标识
     * @return 分数或 null
     */
    @Override
    public Double scoreOf(String board, String member) {
        return boardSet(board).getScore(member);
    }

    /**
     * 查询成员前后各若干名。
     *
     * @param board  榜单标识
     * @param member 成员标识
     * @param range  前后范围
     * @return 排行榜列表
     */
    @Override
    public List<RankItemResponse> around(String board, String member, int range) {
        Long rank = rankOf(board, member);
        if (rank == null) {
            return List.of();
        }
        long start = Math.max(0L, rank - range);
        Collection<ScoredEntry<String>> entries =
                boardSet(board).entryRangeReversed((int) start, (int) (rank + range));
        return toItems(entries, start + 1);
    }

    /**
     * 查询榜单总人数。
     *
     * @param board 榜单标识
     * @return 总人数
     */
    @Override
    public Long size(String board) {
        Integer size = boardSet(board).size();
        return size == null ? 0L : size.longValue();
    }

    /**
     * 移除成员。
     *
     * @param board  榜单标识
     * @param member 成员标识
     */
    @Override
    public void remove(String board, String member) {
        boardSet(board).remove(member);
    }

    /**
     * 结算周榜，把当周数据合并到历史榜。
     *
     * @param board 榜单标识
     * @param date  结算日期
     * @return 历史榜总人数
     */
    @Override
    public Long settleWeekly(String board, LocalDate date) {
        RScoredSortedSet<String> weekly = redissonClient.getScoredSortedSet(weeklyKeyOf(board, date));
        RScoredSortedSet<String> history = redissonClient.getScoredSortedSet(HISTORY + board);
        for (ScoredEntry<String> entry : weekly.entryRange(0, -1)) {
            history.addScore(entry.getValue(), entry.getScore());
        }
        long size = history.size();
        log.info("weekly board settled board={} historySize={}", board, size);
        return size;
    }

    /**
     * 清空整个榜单。
     *
     * @param board 榜单标识
     */
    @Override
    public void clear(String board) {
        boardSet(board).delete();
    }

    /**
     * 获取榜单 ZSet。
     *
     * @param board 榜单标识
     * @return 带分数有序集合
     */
    private RScoredSortedSet<String> boardSet(String board) {
        return redissonClient.getScoredSortedSet(BOARD + board);
    }

    /**
     * 构建周榜键。
     *
     * @param board 榜单标识
     * @param date  日期
     * @return 周榜键
     */
    private String weeklyKeyOf(String board, LocalDate date) {
        WeekFields weekFields = WeekFields.of(Locale.CHINA);
        return WEEKLY + board + ":" + date.get(weekFields.weekBasedYear())
                + "w" + date.get(weekFields.weekOfWeekBasedYear());
    }

    /**
     * 将 ZSet 条目转换为排行榜响应列表。
     *
     * @param entries   带分数条目集合
     * @param firstRank 起始名次
     * @return 排行榜响应列表
     */
    private List<RankItemResponse> toItems(Collection<ScoredEntry<String>> entries, long firstRank) {
        if (entries == null) {
            return List.of();
        }
        List<RankItemResponse> items = new ArrayList<>(entries.size());
        long rank = firstRank;
        for (ScoredEntry<String> entry : entries) {
            items.add(RankItemResponse.of(entry.getValue(), entry.getScore(), rank++));
        }
        return items;
    }

}
