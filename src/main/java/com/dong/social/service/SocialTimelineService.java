package com.dong.social.service;

import com.dong.social.entity.SocialFeed;

import java.util.List;

/**
 * 推模式时间线。发动态时同步写给所有粉丝，读的时候直接取结果，
 * 因此读极快，代价是粉丝量大的账号写放大严重，这也是大 V 必须走拉模式的原因。
 */
public interface SocialTimelineService {

    /**
     * 写扩散，把动态分发给所有粉丝的时间线。
     */
    void fanOutToFollowers(Long authorId, Long feedId, List<Long> followerIds);

    /**
     * 重建某个用户的时间线，新关注时需要补齐历史动态。
     */
    void rebuildTimelineFor(Long userId, List<Long> followeeIds);

    /**
     * 读取时间线。
     */
    List<SocialFeed> readTimeline(Long userId, int size);

}
