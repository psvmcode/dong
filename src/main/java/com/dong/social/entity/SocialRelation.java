package com.dong.social.entity;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * 社交关系。记录用户之间的关注关系，
 * followerId 关注 followeeId，用于实现关注列表与粉丝列表。
 */
@Data

public class SocialRelation {

    /**
     * 主键
     */
    private Long id;

    /**
     * 关注者 id
     */
    private Long followerId;

    /**
     * 被关注者 id
     */
    private Long followeeId;

    /**
     * 关注时间
     */
    private LocalDateTime createTime;

}
