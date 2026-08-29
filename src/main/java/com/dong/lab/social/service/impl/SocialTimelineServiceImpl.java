package com.dong.lab.social.service.impl;

import com.dong.lab.social.entity.SocialFeed;
import com.dong.lab.social.mapper.SocialFeedMapper;
import com.dong.lab.social.service.SocialTimelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialTimelineServiceImpl implements SocialTimelineService {

    private static final String TIMELINE = "lab:social:timeline:";

    private static final int FAN_OUT_BATCH = 500;

    private final SocialFeedMapper socialFeedMapper;

    private final RedissonClient redissonClient;

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

    private RScoredSortedSet<Long> timelineOf(Long userId) {
        return redissonClient.getScoredSortedSet(TIMELINE + userId);
    }

}
