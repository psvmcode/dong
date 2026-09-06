package com.dong.social.service;

import com.dong.social.dto.FeedResponse;

import java.util.List;
import java.util.Map;

/**
 * 微博模型。关注关系用 Set 存储，天然支持交集运算，
 * 共同关注就是一次求交，不需要在应用层循环比对。
 *
 * <p>Feed 流同时实现了推拉两种模式：
 * 推模式写扩散、读极快，适合粉丝量少的普通用户；
 * 拉模式写一份、读时聚合，适合大 V。真实系统通常两者结合。
 */
public interface SocialService {

    /**
     * 关注。
     */
    void follow(Long followerId, Long followeeId);

    /**
     * 取关。
     */
    void unfollow(Long followerId, Long followeeId);

    /**
     * 判断是否已关注。
     */
    boolean isFollowing(Long followerId, Long followeeId);

    /**
     * 查询关注列表。
     */
    List<Long> followees(Long followerId);

    /**
     * 查询粉丝列表。
     */
    List<Long> followers(Long followeeId);

    /**
     * 查询关注数与粉丝数。
     */
    Map<String, Long> counts(Long userId);

    /**
     * 共同关注，即两个关注集合的交集。
     */
    List<Long> commonFollowees(Long firstUserId, Long secondUserId);

    /**
     * 发布动态，同时写入推模式所需的各粉丝时间线。
     */
    long publishFeed(Long authorId, String content);

    /**
     * 推模式时间线，直接读取已准备好的结果。
     */
    List<FeedResponse> timelinePush(Long userId, int size);

    /**
     * 拉模式时间线，读时聚合所有关注者的动态。
     */
    List<FeedResponse> timelinePull(Long userId, int pageNum, int pageSize);

    /**
     * 点赞，返回点赞后总数。
     */
    long like(Long feedId);

    /**
     * 关系总览。
     */
    Map<String, Object> relationSummary(Long userId);

}
