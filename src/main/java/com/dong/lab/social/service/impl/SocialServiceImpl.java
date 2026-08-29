package com.dong.lab.social.service.impl;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import com.dong.lab.common.util.Snowflake;
import com.dong.lab.social.dto.FeedResponse;
import com.dong.lab.social.entity.SocialFeed;
import com.dong.lab.social.entity.SocialRelation;
import com.dong.lab.social.mapper.SocialFeedMapper;
import com.dong.lab.social.mapper.SocialRelationMapper;
import com.dong.lab.social.service.SocialService;
import com.dong.lab.social.service.SocialTimelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialServiceImpl implements SocialService {

    private static final String FOLLOWING = "lab:social:following:";

    private static final String FOLLOWER = "lab:social:follower:";

    private final SocialRelationMapper socialRelationMapper;

    private final SocialFeedMapper socialFeedMapper;

    private final SocialTimelineService socialTimelineService;

    private final RedissonClient redissonClient;

    private final Snowflake snowflake;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void follow(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "cannot follow yourself");
        }
        if (socialRelationMapper.countByFollowerAndFollowee(followerId, followeeId) > 0) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "already following");
        }

        SocialRelation relation = new SocialRelation();
        relation.setFollowerId(followerId);
        relation.setFolloweeId(followeeId);
        socialRelationMapper.insert(relation);

        followingSet(followerId).add(followeeId);
        followerSet(followeeId).add(followerId);
        socialTimelineService.rebuildTimelineFor(followerId, socialRelationMapper.selectFollowees(followerId));

        log.info("user {} followed {}", followerId, followeeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfollow(Long followerId, Long followeeId) {
        socialRelationMapper.delete(followerId, followeeId);
        followingSet(followerId).remove(followeeId);
        followerSet(followeeId).remove(followerId);
        socialTimelineService.rebuildTimelineFor(followerId, socialRelationMapper.selectFollowees(followerId));
        log.info("user {} unfollowed {}", followerId, followeeId);
    }

    @Override
    public boolean isFollowing(Long followerId, Long followeeId) {
        return followingSet(followerId).contains(followeeId);
    }

    @Override
    public List<Long> followees(Long followerId) {
        Collection<Long> cached = followingSet(followerId).readAll();
        return cached == null || cached.isEmpty()
                ? socialRelationMapper.selectFollowees(followerId)
                : List.copyOf(cached);
    }

    @Override
    public List<Long> followers(Long followeeId) {
        Collection<Long> cached = followerSet(followeeId).readAll();
        return cached == null || cached.isEmpty()
                ? socialRelationMapper.selectFollowers(followeeId)
                : List.copyOf(cached);
    }

    @Override
    public Map<String, Long> counts(Long userId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        long following = followingSet(userId).size();
        long followers = followerSet(userId).size();
        counts.put("following", following == 0 ? socialRelationMapper.countFollowees(userId) : following);
        counts.put("followers", followers == 0 ? socialRelationMapper.countFollowers(userId) : followers);
        return counts;
    }

    @Override
    public List<Long> commonFollowees(Long firstUserId, Long secondUserId) {
        Set<Long> first = new HashSet<>(followees(firstUserId));
        Set<Long> second = new HashSet<>(followees(secondUserId));
        first.retainAll(second);
        return List.copyOf(first);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long publishFeed(Long authorId, String content) {
        long feedId = snowflake.nextId();

        SocialFeed feed = new SocialFeed();
        feed.setFeedId(feedId);
        feed.setAuthorId(authorId);
        feed.setContent(content);
        feed.setLikeCount(0L);
        socialFeedMapper.insert(feed);

        socialTimelineService.fanOutToFollowers(authorId, feedId, followers(authorId));
        log.info("feed published feedId={} author={}", feedId, authorId);
        return feedId;
    }

    @Override
    public List<FeedResponse> timelinePush(Long userId, int size) {
        return socialTimelineService.readTimeline(userId, size).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<FeedResponse> timelinePull(Long userId, int pageNum, int pageSize) {
        List<Long> followees = followees(userId);
        if (followees.isEmpty()) {
            return List.of();
        }
        int offset = Math.max(0, pageNum - 1) * pageSize;
        return socialFeedMapper.selectByAuthors(followees, offset, pageSize).stream()
                .map(FeedResponse::from)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long like(Long feedId) {
        socialFeedMapper.increaseLikeCount(feedId);
        SocialFeed feed = socialFeedMapper.selectByFeedId(feedId);
        return feed == null ? 0L : feed.getLikeCount();
    }

    @Override
    public Map<String, Object> relationSummary(Long userId) {
        Map<String, Object> summary = new LinkedHashMap<>(counts(userId));
        summary.put("followeeList", followees(userId));
        summary.put("followerList", followers(userId));
        return summary;
    }

    private FeedResponse toResponse(SocialFeed feed) {
        return FeedResponse.from(feed);
    }

    private RSet<Long> followingSet(Long userId) {
        return redissonClient.getSet(FOLLOWING + userId);
    }

    private RSet<Long> followerSet(Long userId) {
        return redissonClient.getSet(FOLLOWER + userId);
    }

}
