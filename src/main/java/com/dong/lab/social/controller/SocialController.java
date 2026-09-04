package com.dong.lab.social.controller;

import com.dong.lab.common.result.Result;
import com.dong.lab.social.dto.FeedResponse;
import com.dong.lab.social.service.SocialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
/**
 * 微博模型。关注关系用 Set 存储，天然支持交集运算，
 * 共同关注就是一次求交，不需要在应用层循环比对。
 *
 * <p>Feed 流同时实现了推拉两种模式，可以直接对比：
 * 推模式写扩散、读极快，适合粉丝量少的普通用户；
 * 拉模式写一份、读时聚合，适合大 V。真实系统通常两者结合。
 */
@RestController
@RequestMapping("/api/social")
@RequiredArgsConstructor
@Tag(name = "社交关系")

public class SocialController {

    /**
     * socialService，业务服务层。
     */
    private final SocialService socialService;

    /**
     * 关注。
     */
    @PostMapping("/follow")
    @Operation(summary = "关注某个用户")
    public Result<Void> follow(@RequestParam Long followerId, @RequestParam Long followeeId) {
        socialService.follow(followerId, followeeId);
        return Result.success();
    }

    /**
     * 取关。
     */
    @PostMapping("/unfollow")
    @Operation(summary = "取消关注某个用户")
    public Result<Void> unfollow(@RequestParam Long followerId, @RequestParam Long followeeId) {
        socialService.unfollow(followerId, followeeId);
        return Result.success();
    }

    /**
     * 判断是否已关注。
     */
    @GetMapping("/is-following")
    @Operation(summary = "判断是否已关注某个用户")
    public Result<Boolean> isFollowing(@RequestParam Long followerId, @RequestParam Long followeeId) {
        return Result.success(socialService.isFollowing(followerId, followeeId));
    }

    /**
     * 查询关注列表。
     */
    @GetMapping("/followees")
    @Operation(summary = "查询某个用户关注的人")
    public Result<List<Long>> followees(@RequestParam Long followerId) {
        return Result.success(socialService.followees(followerId));
    }

    /**
     * 查询粉丝列表。
     */
    @GetMapping("/followers")
    @Operation(summary = "查询某个用户的粉丝")
    public Result<List<Long>> followers(@RequestParam Long followeeId) {
        return Result.success(socialService.followers(followeeId));
    }

    /**
     * 查询关注数与粉丝数。
     */
    @GetMapping("/counts")
    @Operation(summary = "查询关注数与粉丝数")
    public Result<Map<String, Long>> counts(@RequestParam Long userId) {
        return Result.success(socialService.counts(userId));
    }

    /**
     * 共同关注，即两个用户关注集合的交集。
     */
    @GetMapping("/common-followees")
    @Operation(summary = "查询两个用户的共同关注")
    public Result<List<Long>> commonFollowees(@RequestParam Long firstUserId, @RequestParam Long secondUserId) {
        return Result.success(socialService.commonFollowees(firstUserId, secondUserId));
    }

    /**
     * 发布动态。
     */
    @PostMapping("/feed")
    @Operation(summary = "发布一条动态")
    public Result<Long> publishFeed(@RequestParam Long authorId, @RequestParam String content) {
        return Result.success(socialService.publishFeed(authorId, content));
    }

    /**
     * 推模式时间线。发动态时已写给所有粉丝，这里直接读准备好的结果。
     */
    @GetMapping("/timeline/push")
    @Operation(summary = "推模式时间线，直接读取已准备好的结果")
    public Result<List<FeedResponse>> timelinePush(@RequestParam Long userId,
                                                  @RequestParam(defaultValue = "20") int size) {
        return Result.success(socialService.timelinePush(userId, size));
    }

    /**
     * 拉模式时间线。读的时候才聚合所有关注者的动态。
     */
    @GetMapping("/timeline/pull")
    @Operation(summary = "拉模式时间线，读时聚合所有关注者的动态")
    public Result<List<FeedResponse>> timelinePull(@RequestParam Long userId,
                                                  @RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(socialService.timelinePull(userId, pageNum, pageSize));
    }

    /**
     * 给动态点赞。
     */
    @PostMapping("/feed/like")
    @Operation(summary = "给动态点赞，返回点赞后总数")
    public Result<Long> like(@RequestParam Long feedId) {
        return Result.success(socialService.like(feedId));
    }

    /**
     * 关系总览。
     */
    @GetMapping("/summary")
    @Operation(summary = "查询用户关系总览")
    public Result<Map<String, Object>> summary(@RequestParam Long userId) {
        return Result.success(socialService.relationSummary(userId));
    }

}
