package com.dong.lab.social.service;

import com.dong.lab.social.entity.SocialFeed;

import java.util.List;

public interface SocialTimelineService {

    void fanOutToFollowers(Long authorId, Long feedId, List<Long> followerIds);

    void rebuildTimelineFor(Long userId, List<Long> followeeIds);

    List<SocialFeed> readTimeline(Long userId, int size);

}
