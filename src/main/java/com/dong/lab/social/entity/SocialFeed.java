package com.dong.lab.social.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SocialFeed {

    private Long id;

    private Long feedId;

    private Long authorId;

    private String content;

    private Long likeCount;

    private LocalDateTime createTime;

}
