package com.dong.lab.classic.controller;

import com.dong.lab.classic.dto.RankItemResponse;
import com.dong.lab.classic.service.LeaderboardService;
import com.dong.lab.common.result.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/classic/rank")
@RequiredArgsConstructor
@Tag(name = "classic-rank")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @PostMapping("/submit")
    public Result<Void> submit(@RequestParam(defaultValue = "default") String board,
                               @RequestParam String member,
                               @RequestParam double score) {
        leaderboardService.submit(board, member, score);
        return Result.success();
    }

    @PostMapping("/add")
    public Result<Double> addScore(@RequestParam(defaultValue = "default") String board,
                                   @RequestParam String member,
                                   @RequestParam double delta) {
        return Result.success(leaderboardService.addScore(board, member, delta));
    }

    @GetMapping("/top")
    public Result<List<RankItemResponse>> top(@RequestParam(defaultValue = "default") String board,
                                              @RequestParam(defaultValue = "10") int size) {
        return Result.success(leaderboardService.top(board, size));
    }

    @GetMapping("/rank")
    public Result<Long> rank(@RequestParam(defaultValue = "default") String board,
                             @RequestParam String member) {
        return Result.success(leaderboardService.rankOf(board, member));
    }

    @GetMapping("/score")
    public Result<Double> score(@RequestParam(defaultValue = "default") String board,
                                @RequestParam String member) {
        return Result.success(leaderboardService.scoreOf(board, member));
    }

    @GetMapping("/around")
    public Result<List<RankItemResponse>> around(@RequestParam(defaultValue = "default") String board,
                                                 @RequestParam String member,
                                                 @RequestParam(defaultValue = "2") int range) {
        return Result.success(leaderboardService.around(board, member, range));
    }

    @GetMapping("/size")
    public Result<Long> size(@RequestParam(defaultValue = "default") String board) {
        return Result.success(leaderboardService.size(board));
    }

    @PostMapping("/settle-weekly")
    public Result<Long> settleWeekly(@RequestParam(defaultValue = "default") String board,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(leaderboardService.settleWeekly(board, date == null ? LocalDate.now() : date));
    }

    @PostMapping("/clear")
    public Result<Void> clear(@RequestParam(defaultValue = "default") String board) {
        leaderboardService.clear(board);
        return Result.success();
    }

}
