package com.dong.lab.classic.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShortLink {

    private Long id;

    private String code;

    private String originUrl;

    private Long hitCount;

    private LocalDateTime createTime;

}
