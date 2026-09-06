package com.dong.classic.controller;

import com.dong.classic.dto.RankItemResponse;
import com.dong.classic.service.LeaderboardService;
import com.dong.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
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
/**
 * 排行榜。底层用 Redis ZSet，写入和查询都是对数复杂度，
 * 相比数据库 order by 加 limit，在高频更新场景下代价低得多。
 */
@RestController
@RequestMapping("/api/classic/rank")
@RequiredArgsConstructor
@Tag(name = "经典场景-排行榜")

public class LeaderboardController {

    /**
     * 排行榜服务。
     */
    private final LeaderboardService leaderboardService;

    /**
     * 直接设置分数，已存在则覆盖。
     */
    @PostMapping("/submit")
    @Operation(summary = "提交分数，覆盖该成员原有成绩")
    public Result<Void> submit(@RequestParam(defaultValue = "default") String board,
                               @RequestParam String member,
                               @RequestParam double score) {
        leaderboardService.submit(board, member, score);
        return Result.success();
    }

    /**
     * 在原分数上累加，返回累加后的值。
     */
    @PostMapping("/add")
    @Operation(summary = "累加分数，返回累加后的结果")
    public Result<Double> addScore(@RequestParam(defaultValue = "default") String board,
                                   @RequestParam String member,
                                   @RequestParam double delta) {
        return Result.success(leaderboardService.addScore(board, member, delta));
    }

    /**
     * 取前 N 名。分数相同时按成员字典序排列，这是 ZSet 的默认行为。
     */
    @GetMapping("/top")
    @Operation(summary = "查询排行榜前 N 名")
    public Result<List<RankItemResponse>> top(@RequestParam(defaultValue = "default") String board,
                                              @RequestParam(defaultValue = "10") int size) {
        return Result.success(leaderboardService.top(board, size));
    }

    /**
     * 查询名次，从 0 开始计数。
     */
    @GetMapping("/rank")
    @Operation(summary = "查询某个成员的名次，从 0 开始")
    public Result<Long> rank(@RequestParam(defaultValue = "default") String board,
                             @RequestParam String member) {
        return Result.success(leaderboardService.rankOf(board, member));
    }

    /**
     * 查询分数。成员不存在时返回 null，调用方需要自行处理。
     */
    @GetMapping("/score")
    @Operation(summary = "查询某个成员的分数")
    public Result<Double> score(@RequestParam(defaultValue = "default") String board,
                                @RequestParam String member) {
        return Result.success(leaderboardService.scoreOf(board, member));
    }

    /**
     * 查询某个成员前后各若干名，用于展示"我的位置"这类视图。
     */
    @GetMapping("/around")
    @Operation(summary = "查询某个成员前后指定范围内的排名")
    public Result<List<RankItemResponse>> around(@RequestParam(defaultValue = "default") String board,
                                                 @RequestParam String member,
                                                 @RequestParam(defaultValue = "2") int range) {
        return Result.success(leaderboardService.around(board, member, range));
    }

    /**
     * 总人数。
     */
    @GetMapping("/size")
    @Operation(summary = "查询排行榜总人数")
    public Result<Long> size(@RequestParam(defaultValue = "default") String board) {
        return Result.success(leaderboardService.size(board));
    }

    /**
     * 周榜结算。把当周榜单固化为历史记录并清空当前榜，
     * 否则榜单会无限膨胀，历史数据也无法追溯。
     */
    @PostMapping("/settle-weekly")
    @Operation(summary = "结算周榜，固化历史并清空当前榜单")
    public Result<Long> settleWeekly(@RequestParam(defaultValue = "default") String board,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(leaderboardService.settleWeekly(board, date == null ? LocalDate.now() : date));
    }

    /**
     * 清空整个榜单。
     */
    @PostMapping("/clear")
    @Operation(summary = "清空整个排行榜")
    public Result<Void> clear(@RequestParam(defaultValue = "default") String board) {
        leaderboardService.clear(board);
        return Result.success();
    }

}
