package com.dong.lab.social.entity;

import lombok.Data;

import java.time.LocalDateTime;

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
