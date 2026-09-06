package com.dong.social.entity;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * 社交动态。记录用户发布的内容、点赞数等，
 * 点赞数通过 Redis 异步自增，定时任务刷回数据库。
 */
@Data

public class SocialFeed {

    /**
     * 主键
     */
    private Long id;

    /**
     * 动态 id
     */
    private Long feedId;

    /**
     * 作者 id
     */
    private Long authorId;

    /**
     * 动态内容
     */
    private String content;

    /**
     * 点赞数，Redis 自增后异步落库
     */
    private Long likeCount;

    /**
     * 发布时间
     */
    private LocalDateTime createTime;

}
