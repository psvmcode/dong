package com.dong.lab.social.service;

import com.dong.lab.social.dto.FeedResponse;

import java.util.List;
import java.util.Map;

public interface SocialService {

    void follow(Long followerId, Long followeeId);

    void unfollow(Long followerId, Long followeeId);

    boolean isFollowing(Long followerId, Long followeeId);

    List<Long> followees(Long followerId);

    List<Long> followers(Long followeeId);

    Map<String, Long> counts(Long userId);

    List<Long> commonFollowees(Long firstUserId, Long secondUserId);

    long publishFeed(Long authorId, String content);

    List<FeedResponse> timelinePush(Long userId, int size);

    List<FeedResponse> timelinePull(Long userId, int pageNum, int pageSize);

    long like(Long feedId);

    Map<String, Object> relationSummary(Long userId);

}
