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
/**
 * 微博模型实现。关注关系用两个 Set 双向维护：
 * following 记录我关注的人，follower 记录关注我的人。
 * 多存一份是空间换时间，否则查询粉丝需要全量扫描。
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class SocialServiceImpl implements SocialService {

    private static final String FOLLOWING = "lab:social:following:";

    private static final String FOLLOWER = "lab:social:follower:";

    /**
     * socialRelationMapper，MyBatis Mapper 数据访问层。
     */
    private final SocialRelationMapper socialRelationMapper;

    /**
     * socialFeedMapper，MyBatis Mapper 数据访问层。
     */
    private final SocialFeedMapper socialFeedMapper;

    /**
     * socialTimelineService，业务服务层。
     */
    private final SocialTimelineService socialTimelineService;

    /**
     * Redisson 客户端，用于操作分布式 Set。
     */
    private final RedissonClient redissonClient;

    /**
     * 雪花 ID 生成器。
     */
    private final Snowflake snowflake;

    /**
     * 关注指定用户。
     */
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

    /**
     * 取消关注指定用户。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfollow(Long followerId, Long followeeId) {
        socialRelationMapper.delete(followerId, followeeId);
        followingSet(followerId).remove(followeeId);
        followerSet(followeeId).remove(followerId);
        socialTimelineService.rebuildTimelineFor(followerId, socialRelationMapper.selectFollowees(followerId));
        log.info("user {} unfollowed {}", followerId, followeeId);
    }

    /**
     * 判断是否已关注。
     */
    @Override
    public boolean isFollowing(Long followerId, Long followeeId) {
        return followingSet(followerId).contains(followeeId);
    }

    /**
     * 查询关注列表。
     */
    @Override
    public List<Long> followees(Long followerId) {
        Collection<Long> cached = followingSet(followerId).readAll();
        return cached == null || cached.isEmpty()
                ? socialRelationMapper.selectFollowees(followerId)
                : List.copyOf(cached);
    }

    /**
     * 查询粉丝列表。
     */
    @Override
    public List<Long> followers(Long followeeId) {
        Collection<Long> cached = followerSet(followeeId).readAll();
        return cached == null || cached.isEmpty()
                ? socialRelationMapper.selectFollowers(followeeId)
                : List.copyOf(cached);
    }

    /**
     * 查询关注数与粉丝数。
     */
    @Override
    public Map<String, Long> counts(Long userId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        long following = followingSet(userId).size();
        long followers = followerSet(userId).size();
        counts.put("following", following == 0 ? socialRelationMapper.countFollowees(userId) : following);
        counts.put("followers", followers == 0 ? socialRelationMapper.countFollowers(userId) : followers);
        return counts;
    }

    /**
     * 查询两个用户的共同关注。
     */
    @Override
    public List<Long> commonFollowees(Long firstUserId, Long secondUserId) {
        Set<Long> first = new HashSet<>(followees(firstUserId));
        Set<Long> second = new HashSet<>(followees(secondUserId));
        first.retainAll(second);
        return List.copyOf(first);
    }

    /**
     * 发布动态，并分发给所有粉丝。
     */
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

    /**
     * 查询推模式时间线。
     */
    @Override
    public List<FeedResponse> timelinePush(Long userId, int size) {
        return socialTimelineService.readTimeline(userId, size).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 查询拉模式时间线。
     */
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

    /**
     * 给动态点赞，返回点赞后总数。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public long like(Long feedId) {
        socialFeedMapper.increaseLikeCount(feedId);
        SocialFeed feed = socialFeedMapper.selectByFeedId(feedId);
        return feed == null ? 0L : feed.getLikeCount();
    }

    /**
     * 查询用户关系总览。
     */
    @Override
    public Map<String, Object> relationSummary(Long userId) {
        Map<String, Object> summary = new LinkedHashMap<>(counts(userId));
        summary.put("followeeList", followees(userId));
        summary.put("followerList", followers(userId));
        return summary;
    }

    /**
     * 将动态实体转换为响应对象。
     */
    private FeedResponse toResponse(SocialFeed feed) {
        return FeedResponse.from(feed);
    }

    /**
     * 获取某用户的关注集合。
     */
    private RSet<Long> followingSet(Long userId) {
        return redissonClient.getSet(FOLLOWING + userId);
    }

    /**
     * 获取某用户的粉丝集合。
     */
    private RSet<Long> followerSet(Long userId) {
        return redissonClient.getSet(FOLLOWER + userId);
    }

}
