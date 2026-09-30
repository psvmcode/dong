package com.dong.social.service.impl;

import com.dong.common.constant.Constants;
import com.dong.social.entity.SocialFeed;
import com.dong.social.mapper.SocialFeedMapper;
import com.dong.social.service.SocialTimelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBatch;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 推模式时间线实现。发动态时同步写给所有粉丝，
 * 读的时候直接取结果，代价是粉丝量大的账号写放大严重。
 *
 * <p>三个方法都刻意避开逐条 I/O：粉丝扩散与时间线重建合并成批量 Redis 命令，
 * 时间线读取用一次 in 查询替代 N 次单条查询。
 * 这类循环里的往返是最容易被忽略、也最容易在高粉丝量下爆发的开销。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialTimelineServiceImpl implements SocialTimelineService {

    private static final String TIMELINE = "lab:social:timeline:";

    /**
     * 粉丝扩散的分片大小。批量命令也不宜无限长，
     * 分片是为了控制单次 pipeline 的体积，不是为了让往返变少。
     */
    private static final int FAN_OUT_BATCH = 500;

    /**
     * 重建时间线时最多补齐多少条历史动态。
     */
    private static final int REBUILD_LIMIT = Constants.MAX_BATCH_SIZE;

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
            // 每个粉丝一个 key，逐个 add 就是逐个往返；合并成一次 pipeline 后
            // 一万粉丝从一万次往返降到几十次
            RBatch redisBatch = redissonClient.createBatch();
            for (Long followerId : batch) {
                redisBatch.<Long>getScoredSortedSet(TIMELINE + followerId).addAsync(feedId, feedId);
            }
            redisBatch.execute();
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
        // 一次 in 查询拿回所有关注者的动态，再一次性写进时间线
        List<SocialFeed> feeds = socialFeedMapper.selectByAuthors(followeeIds, 0, REBUILD_LIMIT);
        if (feeds.isEmpty()) {
            log.info("timeline rebuilt for user {} with {} followees, no history feed", userId, followeeIds.size());
            return;
        }
        Map<Long, Double> entries = new LinkedHashMap<>();
        for (SocialFeed feed : feeds) {
            entries.put(feed.getFeedId(), (double) feed.getFeedId());
        }
        timeline.addAll(entries);
        log.info("timeline rebuilt for user {} with {} followees, {} feeds", userId, followeeIds.size(), feeds.size());
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
        List<SocialFeed> feeds = socialFeedMapper.selectByFeedIds(new ArrayList<>(feedIds));
        if (feeds.isEmpty()) {
            return List.of();
        }
        // 顺序由时间线的分数决定，批量查询不保证顺序，必须按原序还原
        Map<Long, SocialFeed> byFeedId = new LinkedHashMap<>();
        for (SocialFeed feed : feeds) {
            byFeedId.put(feed.getFeedId(), feed);
        }
        List<SocialFeed> ordered = new ArrayList<>(feedIds.size());
        for (Long feedId : feedIds) {
            SocialFeed feed = byFeedId.get(feedId);
            if (feed != null) {
                ordered.add(feed);
            }
        }
        return ordered;
    }

    /**
     * 获取某用户的时间线有序集合。
     */
    private RScoredSortedSet<Long> timelineOf(Long userId) {
        return redissonClient.getScoredSortedSet(TIMELINE + userId);
    }

}
