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

@RestController
@RequestMapping("/api/social")
@RequiredArgsConstructor
@Tag(name = "social")
public class SocialController {

    private final SocialService socialService;

    @PostMapping("/follow")
    public Result<Void> follow(@RequestParam Long followerId, @RequestParam Long followeeId) {
        socialService.follow(followerId, followeeId);
        return Result.success();
    }

    @PostMapping("/unfollow")
    public Result<Void> unfollow(@RequestParam Long followerId, @RequestParam Long followeeId) {
        socialService.unfollow(followerId, followeeId);
        return Result.success();
    }

    @GetMapping("/is-following")
    public Result<Boolean> isFollowing(@RequestParam Long followerId, @RequestParam Long followeeId) {
        return Result.success(socialService.isFollowing(followerId, followeeId));
    }

    @GetMapping("/followees")
    public Result<List<Long>> followees(@RequestParam Long followerId) {
        return Result.success(socialService.followees(followerId));
    }

    @GetMapping("/followers")
    public Result<List<Long>> followers(@RequestParam Long followeeId) {
        return Result.success(socialService.followers(followeeId));
    }

    @GetMapping("/counts")
    public Result<Map<String, Long>> counts(@RequestParam Long userId) {
        return Result.success(socialService.counts(userId));
    }

    @GetMapping("/common-followees")
    @Operation(summary = "intersection of two users followee sets, the common friends feature")
    public Result<List<Long>> commonFollowees(@RequestParam Long firstUserId, @RequestParam Long secondUserId) {
        return Result.success(socialService.commonFollowees(firstUserId, secondUserId));
    }

    @PostMapping("/feed")
    public Result<Long> publishFeed(@RequestParam Long authorId, @RequestParam String content) {
        return Result.success(socialService.publishFeed(authorId, content));
    }

    @GetMapping("/timeline/push")
    @Operation(summary = "write fan out, read straight from the prepared timeline")
    public Result<List<FeedResponse>> timelinePush(@RequestParam Long userId,
                                                  @RequestParam(defaultValue = "20") int size) {
        return Result.success(socialService.timelinePush(userId, size));
    }

    @GetMapping("/timeline/pull")
    @Operation(summary = "read fan out, gather from every followee at read time")
    public Result<List<FeedResponse>> timelinePull(@RequestParam Long userId,
                                                  @RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(socialService.timelinePull(userId, pageNum, pageSize));
    }

    @PostMapping("/feed/like")
    public Result<Long> like(@RequestParam Long feedId) {
        return Result.success(socialService.like(feedId));
    }

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(@RequestParam Long userId) {
        return Result.success(socialService.relationSummary(userId));
    }

}
