package com.dong.lab.social.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SocialRelation {

    private Long id;

    private Long followerId;

    private Long followeeId;

    private LocalDateTime createTime;

}
