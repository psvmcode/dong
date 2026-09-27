package com.dong.social.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import com.dong.common.result.Result;
import com.dong.social.dto.CommonFollowQueryRequest;
import com.dong.social.dto.FeedResponse;
import com.dong.social.dto.FollowQueryRequest;
import com.dong.social.dto.SocialUserQueryRequest;
import com.dong.social.dto.TimelinePullQueryRequest;
import com.dong.social.dto.TimelinePushQueryRequest;
import com.dong.social.service.SocialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
@Validated
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
    @PostMapping("/is-following")
    @Operation(summary = "判断是否已关注某个用户")
    public Result<Boolean> isFollowing(@Valid @RequestBody FollowQueryRequest request) {
        return Result.success(socialService.isFollowing(request.getFollowerId(), request.getFolloweeId()));
    }

    /**
     * 查询关注列表。
     */
    @PostMapping("/followees")
    @Operation(summary = "查询某个用户关注的人")
    public Result<List<Long>> followees(@Valid @RequestBody SocialUserQueryRequest request) {
        return Result.success(socialService.followees(request.getUserId()));
    }

    /**
     * 查询粉丝列表。
     */
    @PostMapping("/followers")
    @Operation(summary = "查询某个用户的粉丝")
    public Result<List<Long>> followers(@Valid @RequestBody SocialUserQueryRequest request) {
        return Result.success(socialService.followers(request.getUserId()));
    }

    /**
     * 查询关注数与粉丝数。
     */
    @PostMapping("/counts")
    @Operation(summary = "查询关注数与粉丝数")
    public Result<Map<String, Long>> counts(@Valid @RequestBody SocialUserQueryRequest request) {
        return Result.success(socialService.counts(request.getUserId()));
    }

    /**
     * 共同关注，即两个用户关注集合的交集。
     */
    @PostMapping("/common-followees")
    @Operation(summary = "查询两个用户的共同关注")
    public Result<List<Long>> commonFollowees(@Valid @RequestBody CommonFollowQueryRequest request) {
        return Result.success(socialService.commonFollowees(request.getFirstUserId(), request.getSecondUserId()));
    }

    /**
     * 发布动态。
     */
    @PostMapping("/feed")
    @Operation(summary = "发布一条动态")
    public Result<Long> publishFeed(@RequestParam Long authorId, @RequestParam @NotBlank @Size(max = 4096) String content) {
        return Result.success(socialService.publishFeed(authorId, content));
    }

    /**
     * 推模式时间线。发动态时已写给所有粉丝，这里直接读准备好的结果。
     */
    @PostMapping("/timeline/push")
    @Operation(summary = "推模式时间线，直接读取已准备好的结果")
    public Result<List<FeedResponse>> timelinePush(@Valid @RequestBody TimelinePushQueryRequest request) {
        return Result.success(socialService.timelinePush(request.getUserId(), request.getSize()));
    }

    /**
     * 拉模式时间线。读的时候才聚合所有关注者的动态。
     */
    @PostMapping("/timeline/pull")
    @Operation(summary = "拉模式时间线，读时聚合所有关注者的动态")
    public Result<List<FeedResponse>> timelinePull(@Valid @RequestBody TimelinePullQueryRequest request) {
        return Result.success(socialService.timelinePull(request.getUserId(), request.getPageNum(), request.getPageSize()));
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
    @PostMapping("/summary")
    @Operation(summary = "查询用户关系总览")
    public Result<Map<String, Object>> summary(@Valid @RequestBody SocialUserQueryRequest request) {
        return Result.success(socialService.relationSummary(request.getUserId()));
    }

}
