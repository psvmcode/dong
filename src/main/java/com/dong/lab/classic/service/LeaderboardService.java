package com.dong.lab.classic.service;

import com.dong.lab.classic.dto.RankItemResponse;

import java.time.LocalDate;
import java.util.List;

public interface LeaderboardService {

    void submit(String board, String member, double score);

    Double addScore(String board, String member, double delta);

    List<RankItemResponse> top(String board, int size);

    Long rankOf(String board, String member);

    Double scoreOf(String board, String member);

    List<RankItemResponse> around(String board, String member, int range);

    Long size(String board);

    void remove(String board, String member);

    Long settleWeekly(String board, LocalDate date);

    void clear(String board);

}
