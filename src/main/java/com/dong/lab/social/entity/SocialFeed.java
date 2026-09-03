package com.dong.lab.social.entity;

import lombok.Data;

import java.time.LocalDateTime;

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
