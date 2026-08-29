package com.dong.lab.social.dto;

import com.dong.lab.social.entity.SocialFeed;

import java.time.LocalDateTime;

public class FeedResponse {

    private Long feedId;

    private Long authorId;

    private String content;

    private Long likeCount;

    private LocalDateTime createTime;

    public static FeedResponse from(SocialFeed feed) {
        FeedResponse response = new FeedResponse();
        response.setFeedId(feed.getFeedId());
        response.setAuthorId(feed.getAuthorId());
        response.setContent(feed.getContent());
        response.setLikeCount(feed.getLikeCount());
        response.setCreateTime(feed.getCreateTime());
        return response;
    }

    public Long getFeedId() {
        return feedId;
    }

    public void setFeedId(Long feedId) {
        this.feedId = feedId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(Long likeCount) {
        this.likeCount = likeCount;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

}
