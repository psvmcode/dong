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
     * redissonClient。
     */
    private final RedissonClient redissonClient;

    @Override
    /**
     * submit。
     */
    public void submit(String board, String member, double score) {
        boardSet(board).add(score, member);
        log.info("leaderboard submit board={} member={} score={}", board, member, score);
    }

    @Override
    /**
     * addScore。
     */
    public Double addScore(String board, String member, double delta) {
        return boardSet(board).addScore(member, delta);
    }

    @Override
    /**
     * top。
     */
    public List<RankItemResponse> top(String board, int size) {
        Collection<ScoredEntry<String>> entries = boardSet(board).entryRangeReversed(0, size - 1);
        return toItems(entries, 1L);
    }

    @Override
    /**
     * rankOf。
     */
    public Long rankOf(String board, String member) {
        Integer rank = boardSet(board).revRank(member);
        return rank == null ? null : rank.longValue();
    }

    @Override
    /**
     * scoreOf。
     */
    public Double scoreOf(String board, String member) {
        return boardSet(board).getScore(member);
    }

    @Override
    /**
     * around。
     */
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

    @Override
    /**
     * size。
     */
    public Long size(String board) {
        Integer size = boardSet(board).size();
        return size == null ? 0L : size.longValue();
    }

    @Override
    /**
     * remove。
     */
    public void remove(String board, String member) {
        boardSet(board).remove(member);
    }

    @Override
    /**
     * settleWeekly。
     */
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

    @Override
    /**
     * clear。
     */
    public void clear(String board) {
        boardSet(board).delete();
    }

    /**
     * boardSet。
     */
    private RScoredSortedSet<String> boardSet(String board) {
        return redissonClient.getScoredSortedSet(BOARD + board);
    }

    /**
     * weeklyKeyOf。
     */
    private String weeklyKeyOf(String board, LocalDate date) {
        WeekFields weekFields = WeekFields.of(Locale.CHINA);
        return WEEKLY + board + ":" + date.get(weekFields.weekBasedYear())
                + "w" + date.get(weekFields.weekOfWeekBasedYear());
    }

    /**
     * toItems。
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
