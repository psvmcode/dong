package com.dong.social.service.impl;

import com.dong.social.entity.SocialFeed;
import com.dong.social.mapper.SocialFeedMapper;
import com.dong.social.service.SocialTimelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
/**
 * 推模式时间线实现。发动态时同步写给所有粉丝，
 * 读的时候直接取结果，代价是粉丝量大的账号写放大严重。
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class SocialTimelineServiceImpl implements SocialTimelineService {

    private static final String TIMELINE = "lab:social:timeline:";

    private static final int FAN_OUT_BATCH = 500;

    /**
     * socialFeedMapper，MyBatis Mapper 数据访问层。
     */
    private final SocialFeedMapper socialFeedMapper;

    /**
     * Redisson 客户端，用于操作有序时间线集合。
     */
    private final RedissonClient redissonClient;

    /**
     * 把动态分发给所有粉丝的时间线。
     */
    @Override
    public void fanOutToFollowers(Long authorId, Long feedId, List<Long> followerIds) {
        if (followerIds == null || followerIds.isEmpty()) {
            return;
        }
        for (int i = 0; i < followerIds.size(); i += FAN_OUT_BATCH) {
            List<Long> batch = followerIds.subList(i, Math.min(i + FAN_OUT_BATCH, followerIds.size()));
            batch.forEach(followerId -> timelineOf(followerId).add(feedId, feedId));
        }
        log.info("feed {} fanned out to {} followers", feedId, followerIds.size());
    }

    /**
     * 重建某个用户的时间线，新关注时补齐历史动态。
     */
    @Override
    public void rebuildTimelineFor(Long userId, List<Long> followeeIds) {
        RScoredSortedSet<Long> timeline = timelineOf(userId);
        timeline.delete();
        if (followeeIds == null || followeeIds.isEmpty()) {
            return;
        }
        followeeIds.forEach(followeeId -> socialFeedMapper.selectByAuthor(followeeId)
                .forEach(feed -> timeline.add(feed.getFeedId(), feed.getFeedId())));
        log.info("timeline rebuilt for user {} with {} followees", userId, followeeIds.size());
    }

    /**
     * 读取某用户的时间线。
     */
    @Override
    public List<SocialFeed> readTimeline(Long userId, int size) {
        Collection<Long> feedIds = timelineOf(userId).valueRangeReversed(0, size - 1);
        if (feedIds == null || feedIds.isEmpty()) {
            return List.of();
        }
        List<SocialFeed> feeds = new ArrayList<>(feedIds.size());
        for (Long feedId : feedIds) {
            SocialFeed feed = socialFeedMapper.selectByFeedId(feedId);
            if (feed != null) {
                feeds.add(feed);
            }
        }
        return feeds;
    }

    /**
     * 获取某用户的时间线有序集合。
     */
    private RScoredSortedSet<Long> timelineOf(Long userId) {
        return redissonClient.getScoredSortedSet(TIMELINE + userId);
    }

}
