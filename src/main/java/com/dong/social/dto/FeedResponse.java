package com.dong.social.dto;

import com.dong.social.entity.SocialFeed;

import java.time.LocalDateTime;

/**
 * 动态响应。
 */
public class FeedResponse {

    /**
     * 动态 id。
     */
    private Long feedId;

    /**
     * 作者 id。
     */
    private Long authorId;

    /**
     * 动态内容。
     */
    private String content;

    /**
     * 点赞数。
     */
    private Long likeCount;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 从实体转换为 DTO。
     */
    public static FeedResponse from(SocialFeed feed) {
        FeedResponse response = new FeedResponse();
        response.setFeedId(feed.getFeedId());
        response.setAuthorId(feed.getAuthorId());
        response.setContent(feed.getContent());
        response.setLikeCount(feed.getLikeCount());
        response.setCreateTime(feed.getCreateTime());
        return response;
    }

    /**
     * 获取动态 id。
     */
    public Long getFeedId() {
        return feedId;
    }

    /**
     * 设置动态 id。
     */
    public void setFeedId(Long feedId) {
        this.feedId = feedId;
    }

    /**
     * 获取作者 id。
     */
    public Long getAuthorId() {
        return authorId;
    }

    /**
     * 设置作者 id。
     */
    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    /**
     * 获取动态内容。
     */
    public String getContent() {
        return content;
    }

    /**
     * 设置动态内容。
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * 获取点赞数。
     */
    public Long getLikeCount() {
        return likeCount;
    }

    /**
     * 设置点赞数。
     */
    public void setLikeCount(Long likeCount) {
        this.likeCount = likeCount;
    }

    /**
     * 获取创建时间。
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 设置创建时间。
     */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

}
